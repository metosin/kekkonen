(ns kekkonen.common-test
  (:require [midje.sweet :refer :all]
            [kekkonen.common :as kc]))

(fact "deep-merge"
  (kc/deep-merge {:a 1 :b {:c [1] :d 2}} {:b {:c [2] :d 3 :e 4}}) => {:a 1 :b {:c [2] :d 3 :e 4}})

(fact "deep-merge-from-to"
  (kc/deep-merge-from-to
    {:data {:x String} :request {:body-params {:y String}}}
    [[:data] [:request :body-params]])
  => {:data {:x String} :request {:body-params {:x String, :y String}}})

(fact "deep-merge-to-from"
  (kc/deep-merge-to-from
    {:data {:x String} :request {:body-params {:y String}}}
    [[:request :body-params] [:data]])
  => {:data {:x String} :request {:body-params {:x String, :y String}}})
