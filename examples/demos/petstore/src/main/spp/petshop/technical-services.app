domain petshop;

technical services {
    invocationTrace;
}

intercept services {
    before: invocationTrace;
    after: invocationTrace;
    failure: invocationTrace;
}
