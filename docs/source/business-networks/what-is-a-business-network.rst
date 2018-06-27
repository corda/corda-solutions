What is a Business Network?
---------------------------

If a business network is a group of independent parties transacting together, then its purpose is to allow its
members to create a shared representation of information, or facts, and to then use shared processing of those
facts to achieve agreement, or consensus, about operations involving them.

This ability to enable both shared understanding of facts, and shared understanding about how they are to be used
is something uniquely powerful within DLT/blockchain systems.  Earlier systems focused on the shared representations
of information, but could neither consistently guarantee its correctness, nor ensure that all participants processed
things in the same way.  The Corda promise is that "I know I see what you see" after each operation between involved parties.

Achieving the Corda promise requires shared business logic, which for Corda is reflected in the design and development of CorDapps (Corda Distributed Applications) that are shared among parties engaged in the same business processing. The paramount shift to developing shared business logic in CorDapps not only improves the correctness of the shared data, but also eliminates the expensive and error-prone approach of interacting parties implementing their own interpretation of required business logic. Ultimately, this shared business logic, the CorDapps, form the basis of a business network.

The model of Corda business networks also enables something particularly powerful.  It allows for the possibility
that one business network can build upon the work of another, and that others can then build on top of that.

However, while Corda enables business networks, it deliberately sets out to have few "opinions" about what they might be, or exactly how they should work.  Instead, Corda attempts to define some mechanisms to allow for the
construction of business networks, and leaves the rest rather open-ended.  This flexibility means it is possible
to build both very simple and very complex designs, but, as with most software, over-simplified designs often
miss essential functionality, while over-complex ones are almost impossible to get right.
