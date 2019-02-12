-------------------------------
Expressing Privacy Requirements
-------------------------------

In order to reason about privacy, we need to be able to express which parties should be able to see which data.

We can do this using a simple mapping between the Actors in a design and the aggregate data set for the Cordapp. taking the example of a syndicated loan, the privacy map might look a bit like this:


.. image:: ../resources/privacy/CMN2_P_privacy_map.png
  :width: 80%
  :align: center


Privacy maps can get a little more complicated when we have to consider the visibility of Actors involved in not only this transaction but prior transactions which gave rise to the input states for this transaction. For example, in the Previous Delivery vs Payment example, the Privacy map might look like this:


.. image:: ../resources/privacy/CMN2_P_privacy_map_bond_cash.png
  :width: 90%
  :align: center


