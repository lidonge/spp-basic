'use strict';
function newRequestId() {
  const provider = globalThis.crypto;
  if (typeof provider?.randomUUID === 'function') return provider.randomUUID();
  if (typeof provider?.getRandomValues !== 'function') throw new Error('This browser does not provide secure random numbers');
  const bytes = provider.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
const transportReplays = new WeakMap();
const requestKeys = new WeakMap();
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const money = value => new Intl.NumberFormat('en-US', {style:'currency', currency:'USD'}).format(value ?? 0);
// Display labels belong to the UI; business and transport enum values remain English.
const enumLabels = {
  NORMAL:'Standard', VIP:'VIP', UNPAID:'Unpaid', PAID:'Paid', SHIPPED:'Shipped',
  DELIVERED:'Delivered', CANCELLED:'Cancelled', RETURN_REQUESTED:'Return requested', RETURNED:'Returned',
  COMPLETED:'Completed', ACCEPTED:'Accepted', RESERVED:'Reserved', REJECTED:'Rejected'
};
const enumLabel = value => enumLabels[value] ?? value ?? '';
const actionServices = {
  "Register": "registerShopCustomer",
  "Update profile": "updateShopCustomer",
  "Add goods": "addCartItem",
  "Set quantity": "setCartQuantity",
  "Remove goods": "removeCartItem",
  "Clear cart": "clearShopCart",
  "Place order": "placeShopOrder",
  "Pay": "payShopOrder",
  "Ship": "shipShopOrder",
  "Confirm delivery": "deliverShopOrder",
  "Request return": "requestShopReturn",
  "Approve return": "approveShopReturn",
  "Cancel": "cancelUnpaidShopOrder"
};
const serviceActors = {
  'registerShopCustomer':["customer", "cart"],
  'updateShopCustomer':["customer"],
  'addCartItem':["customer", "cart", "goods", "previousLines"],
  'setCartQuantity':["customer", "cart", "goods", "previousLines"],
  'removeCartItem':["customer", "cart", "goods", "previousLines"],
  'clearShopCart':["Buyer", "Cart", "previousLines"],
  'placeShopOrder':["customer", "cart", "stock", "order", "selection", "products", "receipt", "previousLines"],
  'payShopOrder':["customer", "merchant", "order"],
  'shipShopOrder':["customer", "stock", "order"],
  'deliverShopOrder':["customer", "order"],
  'cancelUnpaidShopOrder':["customer", "stock", "order"],
  'cancelPaidShopOrder':["customer", "merchant", "stock", "order"],
  'requestShopReturn':["Buyer", "ShopOrder"],
  'approveShopReturn':["customer", "merchant", "stock", "order"],
  'previewShopOrder':["customer", "cart", "order", "selection", "products"]
};
let view = {}, lastCommand, lastService, catalog = [];
const errors = {
  PARTICIPANT_NOT_IDENTIFIED:'A required record was not found. Register or load an existing customer first, and check the selected goods or order',
  CUSTOMER_NOT_FOUND:'Register or load the correct customer account first', CUSTOMER_EXISTS:'This membership number is already registered; use Load customer',
  EMPTY_CART:'Add goods to the cart first', INVALID_QUANTITY:'Quantity must be a positive integer', INSUFFICIENT_STOCK:'Insufficient available stock; adjust the cart quantity',
  INSUFFICIENT_FUNDS:'Insufficient demo wallet balance', PRODUCT_UNAVAILABLE:'These goods are not currently available for sale',
  ORDER_NOT_PAYABLE:'Only unpaid orders can be paid', ORDER_NOT_SHIPPABLE:'Only paid orders can be shipped',
  ORDER_NOT_DELIVERABLE:'Only shipped orders can be marked delivered', ORDER_NOT_CANCELLABLE:'Only unpaid or paid but unshipped orders can be cancelled',
  ORDER_NOT_RETURNABLE:'Confirm delivery before requesting a return', RETURN_NOT_REQUESTED:'Submit a return request first',
  REQUEST_ID_REUSED:'Request content differs for the same request or duplicate window', REQUEST_IN_PROGRESS:'The request is being processed; check its result later', REQUEST_RECOVERY_REQUIRED:'The transaction outcome is pending; look up its business receipt', NAME_REQUIRED:'Enter a customer name',
  EMAIL_REQUIRED:'Enter an email address', ADDRESS_REQUIRED:'Enter a delivery address', REASON_REQUIRED:'Enter a cancellation or return reason', TRACKING_REQUIRED:'Enter a tracking number'
};
function notice(text, error=false) { $('notice').textContent=text; $('notice').classList.toggle('error', error); }
const membershipNumber = () => new FormData($('profile')).get('membershipNumber');
function inputFacts(extra={}) { return {membershipNumber:membershipNumber(), ...extra}; }
const participantNames = {"addCartItem":{"customer":"Buyer","cart":"Cart","goods":"Goods","agreement":"CartAdditionAgreement","previousLines":"CartLineArray","CartLine":"CartLineArray"},"updateShopCustomer":{"customer":"Buyer","agreement":"ProfileAgreement"},"priceLine":{},"quoteOrder":{"customer":"Buyer","cart":"Cart","products":"GoodsArray","order":"ShopOrder","Goods":"GoodsArray"},"reserveOrder":{"cart":"Cart","order":"ShopOrder"},"checkOrderOwner":{"customer":"Buyer","order":"ShopOrder"},"preparePayment":{"order":"ShopOrder","agreement":"PaymentAgreement"},"prepareShipment":{"order":"ShopOrder","agreement":"ShipmentAgreement"},"prepareUnpaidCancellation":{"order":"ShopOrder","agreement":"UnpaidCancellationAgreement"},"preparePaidCancellation":{"order":"ShopOrder","agreement":"PaidCancellationAgreement"},"prepareCancellationRefund":{"order":"ShopOrder","agreement":"PaidCancellationAgreement"},"prepareReturnRefund":{"order":"ShopOrder","agreement":"ReturnRefundAgreement"},"prepareDelivery":{"order":"ShopOrder"},"prepareReturnRequest":{"agreement":"ReturnRequestAgreement","order":"ShopOrder"},"prepareReturnApproval":{"order":"ShopOrder","agreement":"ReturnRefundAgreement"},"deliverShopOrder":{"customer":"Buyer","order":"ShopOrder","agreement":"DeliveryAgreement"},"clearShopCart":{"previousLines":"CartLineArray","CartLine":"CartLineArray"},"initializeInventory":{"products":"GoodsArray","inventory":"StockLineArray","Goods":"GoodsArray","StockLine":"StockLineArray"},"reserveInventory":{"inventory":"StockLineArray","order":"ShopOrder","StockLine":"StockLineArray"},"shipInventory":{"inventory":"StockLineArray","agreement":"ShipmentAgreement","StockLine":"StockLineArray"},"releaseUnpaidInventory":{"inventory":"StockLineArray","agreement":"UnpaidCancellationAgreement","StockLine":"StockLineArray"},"releasePaidInventory":{"inventory":"StockLineArray","agreement":"PaidCancellationAgreement","StockLine":"StockLineArray"},"returnInventory":{"inventory":"StockLineArray","agreement":"ReturnRefundAgreement","StockLine":"StockLineArray"},"prepareInventoryOpening":{"products":"GoodsArray","inventory":"StockLineArray","agreement":"InventoryOpeningAgreement","Goods":"GoodsArray","StockLine":"StockLineArray"},"openInventory":{"products":"GoodsArray","inventory":"StockLineArray","agreement":"InventoryOpeningAgreement","Goods":"GoodsArray","StockLine":"StockLineArray"},"reserveRequestedInventory":{"stock":"StockLineArray","agreement":"PetReservationAgreement","StockLine":"StockLineArray"},"reserveShopPet":{"customer":"Buyer","goods":"Goods","stock":"StockLineArray","agreement":"PetReservationAgreement","StockLine":"StockLineArray"},"readShopReservation":{"agreement":"PetReservationAgreement"},"readShopCustomer":{"key":"Buyer"},"readShopCart":{"key":"Cart"},"readShopOrders":{"key":"Buyer","orders":"ShopOrderArray","ShopOrder":"ShopOrderArray"},"readShopInventory":{"key":"StockLineArray","selection":"Goods","products":"GoodsArray","StockLine":"StockLineArray"},"readShopMerchant":{"key":"Merchant"},"prepareProductSelection":{"selection":"Goods"},"filterProducts":{"selection":"Goods","products":"GoodsArray"},"browseShopProducts":{"selection":"Goods","products":"GoodsArray"},"requestShopReturn":{},"cancelPaidShopOrder":{"customer":"Buyer","merchant":"Merchant","stock":"StockLineArray","order":"ShopOrder","agreement":"PaidCancellationAgreement","StockLine":"StockLineArray"},"localizeCatalog":{"products":"GoodsArray","Goods":"GoodsArray"},"importCatalogue":{"selection":"Goods","products":"GoodsArray"},"listOrders":{"ShopOrder":"ShopOrderArray"},"listProducts":{"selection":"Goods","products":"GoodsArray"},"captureCartLines":{"cart":"Cart","previous":"CartLineArray","CartLine":"CartLineArray"},"payOrderFunds":{"customer":"Buyer","merchant":"Merchant","agreement":"PaymentAgreement"},"refundCancellationFunds":{"customer":"Buyer","merchant":"Merchant","agreement":"PaidCancellationAgreement"},"refundReturnFunds":{"customer":"Buyer","merchant":"Merchant","agreement":"ReturnRefundAgreement"},"checkCustomerProfile":{"customer":"Buyer"},"registerCustomer":{"customer":"Buyer","cart":"Cart"},"updateProfile":{"agreement":"ProfileAgreement","customer":"Buyer"},"checkNewCustomer":{"existing":"Buyer"},"checkCart":{"customer":"Buyer","cart":"Cart"},"addCartProduct":{"goods":"Goods","cart":"Cart","agreement":"CartAdditionAgreement"},"setCartProductQuantity":{"goods":"Goods","cart":"Cart","agreement":"CartQuantityAgreement"},"removeCartProduct":{"goods":"Goods","cart":"Cart"},"clearCartProducts":{"cart":"Cart"},"checkBuyerCredential":{"buyer":"Buyer"},"locateBuyerCart":{"buyer":"Buyer","cart":"Cart"},"checkGoodsCredential":{"goods":"Goods"},"checkGoodsSale":{"goods":"Goods"},"prepareInventorySelection":{"products":"GoodsArray","inventory":"StockLineArray","Goods":"GoodsArray","StockLine":"StockLineArray"},"prepareOrderInventory":{"order":"ShopOrder","inventory":"StockLineArray","StockLine":"StockLineArray"},"approveShopReturn":{"customer":"Buyer","merchant":"Merchant","stock":"StockLineArray","order":"ShopOrder","agreement":"ReturnRefundAgreement","StockLine":"StockLineArray"},"checkReservationQuantity":{"agreement":"PetReservationAgreement"},"preparePetReservation":{"customer":"Buyer","goods":"Goods","agreement":"PetReservationAgreement"},"prepareReservationInventory":{"goods":"Goods","stock":"StockLineArray","StockLine":"StockLineArray"},"confirmPetReservation":{"agreement":"PetReservationAgreement"},"removeCartItem":{"customer":"Buyer","cart":"Cart","goods":"Goods","agreement":"CartRemovalAgreement","previousLines":"CartLineArray","CartLine":"CartLineArray"},"shipShopOrder":{"customer":"Buyer","stock":"StockLineArray","order":"ShopOrder","agreement":"ShipmentAgreement","StockLine":"StockLineArray"},"setCartQuantity":{"customer":"Buyer","cart":"Cart","goods":"Goods","agreement":"CartQuantityAgreement","previousLines":"CartLineArray","CartLine":"CartLineArray"},"registerShopCustomer":{"customer":"Buyer","cart":"Cart","agreement":"RegistrationAgreement"},"initializeCatalog":{"products":"GoodsArray","stock":"StockLineArray","merchant":"Merchant","opening":"StoreOpeningAgreement","Goods":"GoodsArray","StockLine":"StockLineArray"},"initializeStore":{"stock":"StockLineArray","merchant":"Merchant","opening":"StoreOpeningAgreement","selection":"Goods","products":"GoodsArray","StockLine":"StockLineArray"},"configureStoreOpening":{"opening":"StoreOpeningAgreement"},"configureMerchantLookup":{"merchant":"Merchant"},"cancelUnpaidShopOrder":{"customer":"Buyer","stock":"StockLineArray","order":"ShopOrder","agreement":"UnpaidCancellationAgreement","StockLine":"StockLineArray"},"payShopOrder":{"customer":"Buyer","merchant":"Merchant","order":"ShopOrder","agreement":"PaymentAgreement"},"initializeRegistrationAgreement":{"buyer":"Buyer","cart":"Cart","agreement":"RegistrationAgreement"},"initializeProfileAgreement":{"buyer":"Buyer","agreement":"ProfileAgreement"},"initializeCartAdditionAgreement":{"buyer":"Buyer","cart":"Cart","goods":"Goods","agreement":"CartAdditionAgreement"},"initializeCartQuantityAgreement":{"buyer":"Buyer","cart":"Cart","goods":"Goods","agreement":"CartQuantityAgreement"},"initializeCartRemovalAgreement":{"buyer":"Buyer","cart":"Cart","goods":"Goods","agreement":"CartRemovalAgreement"},"initializeCartClearingAgreement":{},"initializePaymentAgreement":{"buyer":"Buyer","seller":"Merchant","order":"ShopOrder","agreement":"PaymentAgreement"},"initializeShipmentAgreement":{"buyer":"Buyer","order":"ShopOrder","agreement":"ShipmentAgreement"},"initializeDeliveryAgreement":{"buyer":"Buyer","order":"ShopOrder","agreement":"DeliveryAgreement"},"initializeUnpaidCancellationAgreement":{"buyer":"Buyer","order":"ShopOrder","agreement":"UnpaidCancellationAgreement"},"initializePaidCancellationAgreement":{"buyer":"Buyer","seller":"Merchant","order":"ShopOrder","agreement":"PaidCancellationAgreement"},"initializeReturnRequestAgreement":{},"prepareReturnRequestReceipt":{"order":"ShopOrder","agreement":"ReturnRequestAgreement"},"initializeReturnRefundAgreement":{"buyer":"Buyer","seller":"Merchant","order":"ShopOrder","agreement":"ReturnRefundAgreement"},"preparePaymentAgreementReceipt":{"order":"ShopOrder","agreement":"PaymentAgreement"},"prepareShipmentAgreementReceipt":{"order":"ShopOrder","agreement":"ShipmentAgreement"},"prepareDeliveryAgreementReceipt":{"order":"ShopOrder","agreement":"DeliveryAgreement"},"prepareUnpaidCancellationAgreementReceipt":{"order":"ShopOrder","agreement":"UnpaidCancellationAgreement"},"preparePaidCancellationAgreementReceipt":{"order":"ShopOrder","agreement":"PaidCancellationAgreement"},"prepareReturnRefundAgreementReceipt":{"order":"ShopOrder","agreement":"ReturnRefundAgreement"},"prepareRegistrationAgreementReceipt":{"buyer":"Buyer","agreement":"RegistrationAgreement"},"prepareProfileAgreementReceipt":{"buyer":"Buyer","agreement":"ProfileAgreement"},"prepareCartAdditionAgreementReceipt":{"buyer":"Buyer","agreement":"CartAdditionAgreement"},"prepareCartQuantityAgreementReceipt":{"buyer":"Buyer","agreement":"CartQuantityAgreement"},"prepareCartRemovalAgreementReceipt":{"buyer":"Buyer","agreement":"CartRemovalAgreement"},"prepareCartClearingAgreementReceipt":{},"preparePurchaseReceipt":{"order":"ShopOrder","receipt":"Receipt"},"readRegisteredSubject":{},"checkSubjectFacts":{"subject":"Person"},"checkSubjectAbsent":{"subject":"Person"},"prepareSubjectRegistration":{"subject":"Person","agreement":"SubjectRegistrationAgreement"},"applySubjectProfile":{"subject":"Person","agreement":"SubjectProfileAgreement"},"registerSubject":{"subject":"Person","agreement":"SubjectRegistrationAgreement"},"updateSubjectProfile":{"subject":"Person","agreement":"SubjectProfileAgreement"},"configureMerchantSubject":{"subject":"Person"},"initializeCustomerDomain":{"subject":"Person","agreement":"SubjectRegistrationAgreement"},"placeShopOrder":{"customer":"Buyer","cart":"Cart","stock":"StockLineArray","order":"ShopOrder","receipt":"Receipt","previousLines":"CartLineArray","StockLine":"StockLineArray","selection":"Goods","products":"GoodsArray","CartLine":"CartLineArray"},"previewShopOrder":{"customer":"Buyer","cart":"Cart","order":"ShopOrder","selection":"Goods","products":"GoodsArray"},"listCatalogue":{"selection":"CatalogProduct","products":"CatalogProductArray"},"localizeProductLabels":{"products":"CatalogProductArray","CatalogProduct":"CatalogProductArray"},"configureCatalogueSelection":{"selection":"CatalogProduct"},"initializeProductCatalogue":{"products":"CatalogProductArray","roots":"ProductArray","agreement":"ProductCatalogueOpeningAgreement","CatalogProduct":"CatalogProductArray","Product":"ProductArray"},"initializeProductDomain":{"roots":"ProductArray","agreement":"ProductCatalogueOpeningAgreement","selection":"CatalogProduct","products":"CatalogProductArray","Product":"ProductArray"},"applyProductRevision":{"product":"Product","agreement":"ProductRevisionAgreement"},"checkProductFacts":{"product":"Product"},"reviseProduct":{"product":"Product","agreement":"ProductRevisionAgreement"},"checkQuotationInput":{"cart":"Cart","products":"GoodsArray","Goods":"GoodsArray"},"checkPaymentEligibility":{"customer":"Buyer","order":"ShopOrder"},"checkShipmentAgreementInput":{"agreement":"ShipmentAgreement"},"checkShipmentEligibility":{"order":"ShopOrder"},"checkUnpaidCancellationAgreementInput":{"agreement":"UnpaidCancellationAgreement"},"checkUnpaidCancellation":{"order":"ShopOrder"},"checkPaidCancellationAgreementInput":{"agreement":"PaidCancellationAgreement"},"checkPaidCancellation":{"order":"ShopOrder"},"checkDeliveryEligibility":{"order":"ShopOrder"},"checkReturnRequest":{"order":"ShopOrder"},"checkReturnApproval":{"order":"ShopOrder"},"checkOrderReservation":{"order":"ShopOrder","inventory":"StockLineArray","StockLine":"StockLineArray"},"checkReservedInventory":{"order":"ShopOrder","inventory":"StockLineArray","StockLine":"StockLineArray"},"checkSoldInventory":{"order":"ShopOrder","inventory":"StockLineArray","StockLine":"StockLineArray"},"checkProfileAgreementInput":{"agreement":"ProfileAgreement"},"checkCartAdditionAgreementInput":{"agreement":"CartAdditionAgreement"},"checkCartQuantityAgreementInput":{"agreement":"CartQuantityAgreement"},"checkReturnRequestAgreementInput":{"agreement":"ReturnRequestAgreement"}};
async function invoke(service, participants) {
  const names=participantNames[service] || {};
  const payload=Object.fromEntries(Object.entries(participants).map(([name,value])=>[names[name] || name,value]));
  const response = await fetch('/api/petshop.'+service, {method:'POST', headers:{'Content-Type':'application/json', 'Accept-Language':$('catalog-locale').value, 'X-SPP-Request-Serial':participants.AppHeader?.requestSerialNumber || newRequestId()}, body:JSON.stringify(payload)});
  const data = await response.json();
  if (!response.ok) {
    const failure = new Error(errors[data.error] || data.error || 'Request failed');
    failure.code = data.error;
    failure.participants = data.participants;
    throw failure;
  }
  for (const [alias,name] of Object.entries(names)) if (Object.hasOwn(data.participants,name)) data.participants[alias]=data.participants[name];
  transportReplays.set(data.participants, Boolean(data.replayed));
  return data.participants;
}
async function api(service, cmd) {
  const participants={};
  if (!requestKeys.has(cmd)) requestKeys.set(cmd, newRequestId());
  participants.AppHeader={globalSerialNumber:requestKeys.get(cmd),requestSerialNumber:requestKeys.get(cmd)};
  for (const actor of serviceActors[service]) participants[actor]=['products','stock','previousLines'].includes(actor)?[]:{};
  if (['placeShopOrder','previewShopOrder'].includes(service)) participants.customer={membershipNumber:cmd.membershipNumber};
  if (['registerShopCustomer','updateShopCustomer','addCartItem','setCartQuantity','removeCartItem','clearShopCart'].includes(service)) {
    const buyerName=service==='clearShopCart'?'Buyer':'customer';
    const agreementName=service==='clearShopCart'?'CartClearingAgreement':'agreement';
    participants[buyerName]={membershipNumber:cmd.membershipNumber};
    participants[agreementName]={};
    if (service==='registerShopCustomer') Object.assign(participants.customer,{name:cmd.name,email:cmd.email,address:cmd.address,tier:cmd.tier});
    if (service==='updateShopCustomer') participants.agreement={newName:cmd.name,newEmail:cmd.email,newAddress:cmd.address,newTier:cmd.tier};
    if (['addCartItem','setCartQuantity','removeCartItem'].includes(service)) participants.goods={sku:cmd.sku};
    if (['addCartItem','setCartQuantity'].includes(service)) participants.agreement.quantity=cmd.quantity;
  }
  if (['payShopOrder','shipShopOrder','deliverShopOrder','cancelUnpaidShopOrder','cancelPaidShopOrder','approveShopReturn'].includes(service)) {
    participants.customer={membershipNumber:cmd.membershipNumber};
    participants.order={orderNumber:cmd.orderNumber};
    participants.agreement=service==='shipShopOrder'?{tracking:cmd.tracking}
      :['cancelUnpaidShopOrder','cancelPaidShopOrder'].includes(service)?{reason:cmd.reason}:{};
  }
  if (service==='requestShopReturn') {
    participants.Buyer={membershipNumber:cmd.membershipNumber};
    participants.ShopOrder={orderNumber:cmd.orderNumber};
    participants.ReturnRequestAgreement={reason:cmd.reason};
  }
  const data=await invoke(service,participants);
  const agreement=data.agreement || data.CartClearingAgreement || data.ReturnRequestAgreement;
  return {view:{receipt:data.receipt || agreement?.receipt || agreement?.Receipt,
    replayed:transportReplays.get(data),order:data.order || data.ShopOrder}};
}
async function readOptionalParticipant(service, participants, name) {
  try {
    const facts = await invoke(service, participants);
    return {[name]: facts.key};
  } catch (failure) {
    if (failure.code === 'PARTICIPANT_NOT_IDENTIFIED') return {[name]: null};
    throw failure;
  }
}
async function refresh() {
  const number=membershipNumber();
  const results=await Promise.all([
    invoke('browseShopProducts',{selection:{category:$('category').value},products:[]}).then(p=>({products:p.products})),
    invoke('readShopInventory',{selection:{},products:[],key:[]}).then(p=>({inventory:p.key})),
    invoke('readShopMerchant',{key:{}}).then(p=>({merchant:p.key})),
    readOptionalParticipant('readShopCustomer',{key:{membershipNumber:number}},'customer'),
    readOptionalParticipant('readShopCart',{key:{buyer:{membershipNumber:number}}},'cart'),
    invoke('readShopOrders',{key:{membershipNumber:number},orders:[]}).then(p=>({orders:p.orders}))
  ]);
  view=Object.assign({},...results);
  catalog=[...new Map([...catalog,...(view.products || [])].map(p=>[p.sku,p])).values()];
  const selectedCategory=$('category').value;
  const categories=[...new Set(catalog.map(product=>product.category).filter(Boolean))];
  $('category').innerHTML='<option value="">All categories</option>'+categories.map(category=>'<option value="'+esc(category)+'">'+esc(category)+'</option>').join('');
  $('category').value=selectedCategory;
  render();
}
async function mutate(action, extra={}, service=actionServices[action]) {
  const cmd=inputFacts(extra); const result=await api(service,cmd);
  lastCommand=cmd; lastService=service; showReceipt(result.view); await refresh(); notice(action+' completed');
  return result;
}
function showReceipt(data) { $('receipt').innerHTML='<strong>Business receipt</strong><br>'+esc(data.receipt?.receiptNumber)+'<br>'+esc(enumLabel(data.receipt?.orderStatus ?? data.receipt?.status))+(data.replayed?'<br>Original receipt returned':''); }
function render() {
  const chosenReservation = $('reservation-product').value;
  $('reservation-product').innerHTML = (view.products || []).map(product => '<option value="'+esc(product.sku)+'">'+esc(product.name)+'</option>').join('');
  if (chosenReservation) $('reservation-product').value = chosenReservation;
  $('customer').innerHTML=view.customer?.name?'<strong>'+esc(view.customer.name)+'</strong> · '+esc(enumLabel(view.customer.tier))+' · Demo wallet <span class="metric">'+money(view.customer.wallet)+'</span>':'Customer not registered. Create a demo account first.';
  $('products').innerHTML=(view.products || []).map(p=>{const row=(view.inventory || []).find(r=>r.goods?.sku===p.sku);return '<article class="product"><div class="product-icon">'+({'Cats':'🐈','Dogs':'🐕','Food':'🥣','Accessories':'🪶'}[p.category] || '🐾')+'</div><small>'+esc(p.category)+'</small><h3>'+esc(p.name)+'</h3><p>'+esc(p.description)+'</p><div class="price">'+money(p.price)+'</div><div class="stock">Available '+esc(row?.available)+' · Reserved '+esc(row?.reserved)+' · Sold '+esc(row?.sold)+'</div><div class="form-actions"><input type="number" value="1" min="1" aria-label="Quantity"><button data-add="'+esc(p.sku)+'">Add to cart</button></div></article>';}).join('');
  $('cart-lines').innerHTML=(view.cart?.lines || []).map(line=>'<div class="cart-row"><span>'+esc(catalog.find(p=>p.sku===line.goods?.sku)?.name || line.goods?.sku)+'</span><input type="number" min="1" value="'+esc(line.quantity)+'" data-quantity="'+esc(line.goods?.sku)+'" aria-label="Cart quantity"><button class="text-button" data-remove="'+esc(line.goods?.sku)+'">Remove</button></div>').join('') || '<p>Your cart is empty.</p>';
  $('quote').textContent='';
  $('inventory').innerHTML='Per-product stock is shown on each card. Totals are calculated on demand rather than stored as independent state.<br>Merchant funds: <span class="metric">'+money(view.merchant?.cash)+'</span>';
  $('order-list').innerHTML=(view.orders || []).map(order=>'<article class="order"><div class="order-header"><h3>'+esc(order.orderNumber)+'</h3><span class="badge">'+esc(enumLabel(order.status))+'</span></div><div class="order-lines">'+(order.lines || []).map(line=>(line.lineNumber ? 'Line reference '+esc(line.lineNumber)+' · ' : '')+esc(line.name)+' × '+esc(line.quantity)+' · '+money(line.unitPrice)+' = '+money(line.amount)).join('<br>')+'</div><div>Goods '+money(order.subtotal)+' − Discount '+money(order.discount)+' + Shipping '+money(order.shipping)+' = <strong>'+money(order.total)+'</strong></div><p class="hint">Agreed address: '+esc(order.address)+' · Tracking number: '+esc(order.tracking || 'Not shipped')+'</p><label>Cancellation/return reason or tracking number<input value="Demo reason" data-detail="'+esc(order.orderNumber)+'"></label><div class="order-actions">'+['Pay','Ship','Confirm delivery','Cancel','Request return','Approve return'].map(action=>'<button class="secondary" data-order="'+esc(order.orderNumber)+'" data-action="'+action+'">'+action+'</button>').join('')+'</div></article>').join('') || '<div class="panel"><p>No orders yet. Create your first order from the cart.</p></div>';
}
async function guarded(fn) { const buttons=[...document.querySelectorAll('button')];buttons.forEach(b=>b.disabled=true);try{await fn();}catch(e){notice(e.message,true);}finally{buttons.forEach(b=>b.disabled=false);} }
$('profile').addEventListener('submit',e=>{e.preventDefault();guarded(()=>mutate('Register',Object.fromEntries(new FormData(e.target))));});
$('load-customer').onclick=()=>guarded(async()=>{await refresh();notice('Customer facts refreshed');});
$('update-profile').onclick=()=>guarded(()=>mutate('Update profile',Object.fromEntries(new FormData($('profile')))));
$('category').onchange=()=>guarded(refresh);
$('refresh').onclick=()=>guarded(refresh);
$('clear-cart').onclick=()=>guarded(()=>mutate('Clear cart'));
$('preview').onclick=()=>guarded(async()=>{const work=await api('previewShopOrder',inputFacts());const order=work.view.order;$('quote').innerHTML='Goods '+money(order.subtotal)+' − Discount '+money(order.discount)+' + Shipping '+money(order.shipping)+'<br><strong>Quote total '+money(order.total)+'</strong>';notice('Quote received; inventory has not been reserved');});
$('checkout').onclick=()=>guarded(()=>mutate('Place order'));
$('replay').onclick=()=>guarded(async()=>{if(!lastCommand)throw new Error('No request is available to replay');const work=await api(lastService,lastCommand);showReceipt(work.view);await refresh();notice('Original request replayed');});
$('products').onclick=e=>{const button=e.target.closest('[data-add]');if(button)guarded(()=>mutate('Add goods',{sku:button.dataset.add,quantity:Number(button.parentElement.querySelector('input').value)}));};
$('cart-lines').onclick=e=>{const button=e.target.closest('[data-remove]');if(button)guarded(()=>mutate('Remove goods',{sku:button.dataset.remove}));};
$('cart-lines').onchange=e=>{if(e.target.dataset.quantity)guarded(()=>mutate('Set quantity',{sku:e.target.dataset.quantity,quantity:Number(e.target.value)}));};
$('order-list').onclick=e=>{const button=e.target.closest('[data-order]');if(!button)return;const detail=button.closest('.order').querySelector('[data-detail]').value;const action=button.dataset.action; const order=(view.orders || []).find(o=>o.orderNumber===button.dataset.order); const service=action==='Cancel'?(order?.status==='PAID'?'cancelPaidShopOrder':'cancelUnpaidShopOrder'):actionServices[action]; guarded(()=>mutate(action,{orderNumber:button.dataset.order,reason:detail,tracking:detail},service));};
guarded(async()=>{await refresh();notice(view.customer ? 'Shop facts loaded' : 'Shop facts loaded. Register or load a customer first');});

$('catalog-locale').addEventListener('change', () => { $('category').value=''; catalog=[]; refresh().catch(error => notice(error.message, true)); });

function showReservation(agreement) {
  if (!agreement) return;
  $('reservation-number').value = agreement.reservationNumber || '';
  $('reservation-facts').textContent = 'Reservation number: ' + (agreement.reservationNumber || 'Not accepted yet')
    + ' · Status: ' + enumLabel(agreement.status) + ' · Goods: ' + (agreement.goods?.sku || '')
    + ' · Quantity: ' + (agreement.quantity ?? '');
}
$('reserve-pet').onclick = () => guarded(async () => {
  try {
    const result = await invoke('reserveShopPet', {
      AppHeader: {globalSerialNumber: newRequestId()},
      customer: {membershipNumber: membershipNumber()}, goods: {sku: $('reservation-product').value},
      stock: [], agreement: {quantity: Number($('reservation-quantity').value)}
    });
    showReservation(result.agreement); notice('Reservation outcome returned');
  } catch (failure) {
    showReservation(failure.participants?.agreement);
    throw failure;
  } finally { await refresh(); }
});
$('read-reservation').onclick = () => guarded(async () => {
  const result = await invoke('readShopReservation', {agreement: {reservationNumber: $('reservation-number').value}});
  showReservation(result.agreement); notice('Reservation facts refreshed');
});

$('fund-wallet').onclick = () => guarded(async () => {
  const result = await invoke('fundShopWallet', {Buyer: {membershipNumber: membershipNumber()}, SettlementAccount: {},
    WalletFundingAgreement: {amount: $('funding-amount').value}});
  await refresh(); notice('Funds transferred from the settlement account to the buyer wallet; settlement source balance: ' + money(result.SettlementAccount.balance));
});
$('receive-stock').onclick = () => guarded(async () => {
  const sources = [];
  for (const product of catalog) {
    const result = await invoke('receiveShopStock', {Goods: {sku: product.sku}, StockLine: {}, StockSupply: {},
      StockReceiptAgreement: {quantity: Number($('receipt-quantity').value)}});
    sources.push(product.sku + ': ' + result.StockSupply.balance);
  }
  await refresh(); notice('Selected goods received from their supply sources; each product settled separately. Supply source balance ' + sources.join(', '));
});
