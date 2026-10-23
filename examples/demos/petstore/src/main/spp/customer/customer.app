domain customer;

application customerService {
    environment testing {
        deploy customer;
        quantum {
            system: UNIFIED_STORE;
            resource: shopLedger;
        }
    }
}

header requestContext {
    unique String globalSerialNumber;
    String callerId;
    String requestSerialNumber;
    String callMode;
    String callOrigin;
}

header responseContext {
    String status(Default = "OK");
}

tables entities {
    table PERSON_REGISTRY(Person) {
        primarykey(Person.customerNumber);
        auto Person.customerNumber prefix "CUSTOMER";
    }
}

scope {
    local(in requestContext, out responseContext) {
    }
}

web {
    start initializeCustomerDomain;
    expose registerSubject;
    expose updateSubjectProfile;
}
