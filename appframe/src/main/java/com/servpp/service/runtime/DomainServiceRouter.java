package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.Service;
import com.servpp.service.repository.TransactionQueryReads;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Deployment-driven local/remote routing, independent of business participant syntax. */
public final class DomainServiceRouter {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Map<String,Service> PROTOTYPES = new ConcurrentHashMap<>();
    private static final Map<String,Route> ROUTES = new LinkedHashMap<>();
    private static final ThreadLocal<Map<String,AtomicInteger>> CALLS = ThreadLocal.withInitial(ConcurrentHashMap::new);
    private static final ThreadLocal<String> CALL_SITE = new ThreadLocal<>();
    private static volatile DomainDeploymentManifest manifest;
    private static volatile boolean ready = true;
    private static String rpcToken;
    private static String callerIdentity;
    private record Route(URI endpoint,int timeoutMillis) { }
    private DomainServiceRouter() { }
    static Properties catalog(String domain) {
        Properties values = new Properties();
        try (var input = DomainServiceRouter.class.getResourceAsStream("/META-INF/spp/domains/"+domain+"/services.properties")) {
            if (input == null) throw new IllegalStateException("DOMAIN_SERVICE_CATALOG_REQUIRED: "+domain);
            values.load(input); return values;
        } catch (java.io.IOException failure) { throw new IllegalStateException("DOMAIN_SERVICE_CATALOG_FAILED",failure); }
    }
    static Service prototype(String qualifiedService) {
        return PROTOTYPES.computeIfAbsent(qualifiedService,name -> {
            String domain = name.substring(0,name.indexOf('.'));
            String type = catalog(domain).getProperty(name);
            if (type == null) throw new IllegalStateException("DOMAIN_SERVICE_NOT_DECLARED: "+name);
            try { return (Service)Class.forName(type).getConstructor().newInstance(); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("DOMAIN_SERVICE_PROTOTYPE_FAILED",failure); }
        });
    }
    public static void configure(Properties application,String prefix,List<String> domains,Map<String,Properties> physical) {
        ROUTES.clear(); PROTOTYPES.clear(); rpcToken=null;
        callerIdentity=application.getProperty(prefix+"environment","legacy")+":"+application.getProperty(prefix+"name");
        for (String domain : domains) {
            String token=PhysicalRuntimeCatalog.resolveConfigValue(physical.get(domain).getProperty("config.domainRpcToken", ""));
            if (!token.isBlank()) {
                if (rpcToken!=null && !rpcToken.equals(token)) throw new IllegalStateException("DOMAIN_RPC_TRUST_CONFIGURATION_CONFLICT");
                rpcToken=token;
            }
        }
        for (String domain : domains) {
            Properties values = physical.get(domain);
            for (String target : values.getProperty("domainRoutes","").split(",")) {
                if (target.isBlank()) continue;
                String root="domainRoute."+target+".";
                URI endpoint=URI.create(PhysicalRuntimeCatalog.resolveConfigValue(values.getProperty(root+"endpoint")));
                if (!Set.of("http","https").contains(endpoint.getScheme()) || endpoint.getHost()==null
                        || endpoint.getUserInfo()!=null || endpoint.getQuery()!=null || endpoint.getFragment()!=null)
                    throw new IllegalStateException("DOMAIN_ROUTE_ENDPOINT_INVALID: "+target);
                Route route=new Route(endpoint,Integer.parseInt(values.getProperty(root+"timeoutMillis","5000")));
                if (route.timeoutMillis()<=0) throw new IllegalStateException("DOMAIN_ROUTE_TIMEOUT_INVALID");
                Route previous=ROUTES.putIfAbsent(target,route);
                if (previous!=null && !previous.equals(route)) throw new IllegalStateException("DOMAIN_ROUTE_CONFLICT: "+target);
                if (domains.contains(target)) throw new IllegalStateException("DOMAIN_ROUTE_TARGET_ALREADY_LOCAL: "+target);
            }
        }
        if (!ROUTES.isEmpty() && rpcToken==null) throw new IllegalStateException("DOMAIN_RPC_TRUST_CONFIGURATION_REQUIRED");
        manifest=DomainDeploymentManifest.create(application,prefix,domains,physical); ready=false;
    }
    public static boolean accepts(String token) {
        return rpcToken!=null && token!=null && java.security.MessageDigest.isEqual(
                rpcToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static boolean isRemote(String service) { return ROUTES.containsKey(service.substring(0,service.indexOf('.'))); }
    static String localOrigin(Object participant, String fallback) {
        if (manifest!=null && participant!=null) for (String domain : manifest.domains())
            if (participant.getClass().getName().contains("."+domain+".model.")) return domain;
        return fallback;
    }
    public static DomainDeploymentManifest manifest() { return manifest; }
    public static boolean isReady() { return ready; }
    public static void markReady() { ready=true; }
    public static void verifyPeers() {
        for (var route : ROUTES.entrySet()) {
            DomainDeploymentManifest peer=readManifest(route.getValue()); manifest.requireCompatible(peer,route.getKey());
            requirePeerReady(route.getValue());
        }
    }
    private static void requirePeerReady(Route route) {
        try {
            HttpResponse<String> response=CLIENT.send(HttpRequest.newBuilder(uri(route,"/spp/readiness"))
                    .timeout(Duration.ofMillis(route.timeoutMillis())).GET().build(),HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200 || !MAPPER.readTree(response.body()).path("ready").asBoolean(false))
                throw new IllegalStateException("REMOTE_DOMAIN_NOT_READY: start provider applications before consumers");
        } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("DOMAIN_DEPLOYMENT_CHECK_INTERRUPTED",failure); }
        catch (java.io.IOException failure) { throw new IllegalStateException("REMOTE_DOMAIN_READINESS_UNAVAILABLE",failure); }
    }
    private static DomainDeploymentManifest readManifest(Route route) {
        try {
            HttpResponse<String> response=CLIENT.send(HttpRequest.newBuilder(uri(route,"/spp/deployment"))
                    .timeout(Duration.ofMillis(route.timeoutMillis())).GET().build(),HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200) throw new IllegalStateException("REMOTE_DOMAIN_CONTRACT_UNAVAILABLE");
            return MAPPER.readValue(response.body(),DomainDeploymentManifest.class);
        } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("DOMAIN_DEPLOYMENT_CHECK_INTERRUPTED",failure); }
        catch (java.io.IOException failure) { throw new IllegalStateException("REMOTE_DOMAIN_CONTRACT_UNAVAILABLE",failure); }
    }
    static Object[] invoke(String name,Service prototype,Object[] participants,java.lang.reflect.Type[] types) {

        if (prototype instanceof QuantumServiceMetadataProvider quantum && !quantum.getQuantumAttributeIds().isEmpty())
            throw new IllegalStateException("REMOTE_DOMAIN_QUANTUM_RECEIPT_PROTOCOL_REQUIRED");
        for (Object participant : participants) if (TransactionQueryReads.protects(participant,true)
                && prototype instanceof GeneratedServiceKindProvider kind && kind.getGeneratedServiceKind()==GeneratedServiceKind.ATOMIC_QUERY)
            throw new IllegalStateException("REMOTE_TRANSACTION_QUERY_LOCK_PROTOCOL_REQUIRED");
        String domain=name.substring(0,name.indexOf('.')); Route route=ROUTES.get(domain);
        String trace=com.servpp.service.transaction.BusinessTraceContext.currentGlobalSerialNumber().orElseGet(() -> UUID.randomUUID().toString());
        Map<String,String> transaction=DomainServiceProtocol.captureTransaction();
        String callKey=Objects.toString(CALL_SITE.get(),"runtime")+":"+name;
        String serial=trace+":"+callKey+":"+CALLS.get().computeIfAbsent(callKey,ignored -> new AtomicInteger()).getAndIncrement()
                + (transaction.containsKey("xid")?":"+transaction.get("xid"):"");
        var facts=new ArrayList<com.fasterxml.jackson.databind.JsonNode>(); var state=new ArrayList<ParticipantWireState.State>();
        for (Object participant : participants) { facts.add(MAPPER.valueToTree(participant)); state.add(ParticipantWireState.capture(participant,manifest.domains().get(0))); }
        var call=new DomainServiceProtocol.Call(name,DomainServiceProtocol.signature(prototype),facts,state,trace,transaction);
        try {
            HttpRequest request=HttpRequest.newBuilder(uri(route,"/spp/atomic"))
                    .timeout(Duration.ofMillis(route.timeoutMillis())).header("Content-Type","application/json")
                    .header("X-SPP-Domain-Token",rpcToken)
                    .header("X-SPP-Caller",callerIdentity).header("X-SPP-Request-Serial",serial)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(call))).build();
            HttpResponse<String> response=CLIENT.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200) {
                var body=MAPPER.readTree(response.body());
                var failure=new IllegalStateException(body.path("error").asText("REMOTE_DOMAIN_SERVICE_FAILED"));
                var outcome=com.servpp.service.transaction.TransactionOutcomes.Outcome.UNKNOWN;
                try { outcome=com.servpp.service.transaction.TransactionOutcomes.Outcome.valueOf(body.path("outcome").asText("UNKNOWN")); }
                catch (IllegalArgumentException ignored) { /* An unrecognized peer outcome is uncertain. */ }
                if (outcome==com.servpp.service.transaction.TransactionOutcomes.Outcome.UNREPORTED)
                    outcome=com.servpp.service.transaction.TransactionOutcomes.Outcome.UNKNOWN;
                com.servpp.service.transaction.TransactionOutcomes.record(failure,outcome);
                throw failure;
            }
            var result=MAPPER.readValue(response.body(),DomainServiceProtocol.Result.class);
            if (result.participants().size()!=types.length || result.state().size()!=types.length)
                throw new IllegalStateException("REMOTE_DOMAIN_PARTICIPANT_ARITY_MISMATCH");
            Object[] returned=new Object[types.length];
            for (int index=0;index<types.length;index++) {
                returned[index]=MAPPER.convertValue(result.participants().get(index),MAPPER.getTypeFactory().constructType(types[index]));
                ParticipantWireState.restore(returned[index],result.state().get(index));
            }
            if (result.check()!=null && !result.check().passed()) BusinessChecks.fail(result.check().code());
            return returned;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            var unknown=new IllegalStateException("REMOTE_DOMAIN_CALL_INTERRUPTED",failure);
            com.servpp.service.transaction.TransactionOutcomes.record(unknown,com.servpp.service.transaction.TransactionOutcomes.Outcome.UNKNOWN);
            throw unknown;
        }
        catch (java.io.IOException failure) {
            var unknown=new IllegalStateException("REMOTE_DOMAIN_OUTCOME_UNKNOWN",failure);
            com.servpp.service.transaction.TransactionOutcomes.record(unknown,com.servpp.service.transaction.TransactionOutcomes.Outcome.UNKNOWN);
            throw unknown;
        }
    }
    private static URI uri(Route route,String path) { return URI.create(route.endpoint().toString().replaceAll("/$","")+path); }
    /** Generated lexical call sites separate parallel occurrences of the same atomic service. */
    public static void atCallSite(String site, Runnable action) {
        String previous=CALL_SITE.get();
        CALL_SITE.set(previous==null?site:previous+"/"+site);
        try { action.run(); } finally { restoreCallSite(previous); }
    }
    private static void restoreCallSite(String site) {
        if (site==null) CALL_SITE.remove(); else CALL_SITE.set(site);
    }
    static void executeScope(Runnable action) {
        var previous=CALLS.get(); String previousSite=CALL_SITE.get();
        CALLS.set(new ConcurrentHashMap<>()); CALL_SITE.remove();
        try { action.run(); } finally { CALLS.set(previous); restoreCallSite(previousSite); }
    }
    static Runnable bindScope(Runnable action) {
        var shared=CALLS.get(); String sharedSite=CALL_SITE.get();
        return () -> {
            var previous=CALLS.get(); String previousSite=CALL_SITE.get();
            CALLS.set(shared); restoreCallSite(sharedSite);
            try { action.run(); } finally { CALLS.set(previous); restoreCallSite(previousSite); }
        };
    }
}
