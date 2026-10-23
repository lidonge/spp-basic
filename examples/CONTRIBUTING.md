# Contributing

Issues and small, focused contributions are welcome under the parent SDK's
[Agent contribution policy](../CONTRIBUTING.md). Include a concrete scenario,
source file, expected and observed behavior. Do not submit real customer data,
credentials, proprietary bank source, compiled artifacts or database files.

## Demo and website code

Changes to demo and website code are contributed under the repository's MIT code license. Production demo business behavior belongs in SPP; APP/PHY describe realization. Do not add handwritten production business Java or edit generated output. Java tests may arrange participants, invoke generated services, assert outcomes and inject technical faults.

For source changes, preserve explicit participants, service boundaries, visible checks and transactions. Maintain English defaults and USD presentation. For formatting-only changes, check that lexical tokens are unchanged. Run relevant generated-service acceptance tests when the S++ SDK is available.

Website changes belong in the separate public spp-examples repository, using its
build_book.py/check_site.py workflow and desktop/mobile checks. Source displays
preserve actual line breaks, not automatic wrapping as a substitute for formatting.

## Book corrections

The book is publicly readable but is not MIT-licensed or Creative Commons licensed. Its terms are in `book/COPYRIGHT.md`. Report corrections in an issue; do not assume that submitting an issue or code pull request grants rights to the book. Obtain written agreement before submitting substantive replacement manuscript content or translations. Existing rights and applicable-law permissions remain unchanged.

The maintainer reviews and releases changes. The public website is generated from
the separate website repository; do not maintain live-server-only edits.
