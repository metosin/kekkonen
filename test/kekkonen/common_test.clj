(ns kekkonen.common-test
  (:require [midje.sweet :refer :all]
            [kekkonen.common :as kc]))

(fact "deep-merge"
  (kc/deep-merge {:a 1 :b {:c [1] :d 2}} {:b {:c [2] :d 3 :e 4}}) => {:a 1 :b {:c [2] :d 3 :e 4}})
