(ns kekkonen.common-test
  (:require [midje.sweet :refer :all]
            [kekkonen.common :as kc]
            [schema.core :as s]
            [plumbing.core :as p]))

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

(fact "strip-nil-values"
  (kc/strip-nil-values {:a {:b {:c {:e nil}}, :b2 true}}) => {:a {:b2 true}})

(fact "copy-from-to"
  (kc/copy-from-to
    {:request {:body-params {:x String, :y String}}}
    [[:request :body-params] [:data]])
  => {:request {:body-params {:x String, :y String}}
      :data {:x String, :y String}})

(fact "copy-to-fom"
  (kc/copy-to-from
    {:request {:body-params {:x String, :y String}}}
    [[:data] [:request :body-params]])
  => {:request {:body-params {:x String, :y String}}
      :data {:x String, :y String}})

(fact "move-from-to"
  (kc/move-from-to
    {:request {:body-params {:x String, :y String}}}
    [[:request :body-params] [:data]])
  => {:data {:x String, :y String}})

(fact "move-to-from"
  (kc/move-to-from
    {:data {:x String, :y String}}
    [[:request :body-params] [:data]])
  => {:request {:body-params {:x String, :y String}}}

  (fact "will not copy nil data"
    (kc/move-to-from
      {:request {:body-params {:x String, :y String}}}
      [[:request :body-params] [:data]])
    => {:request {:body-params {:x String, :y String}}}))

(fact "merge-map-schemas"
  (kc/merge-map-schemas s/Any {:a s/Str}) => {:a s/Str}
  (kc/merge-map-schemas s/Any s/Any) => {}
  (kc/merge-map-schemas {:a s/Str} {:a {:b s/Str}}) => {:a {:b s/Str}})

(fact "any-map-schema?"
  (kc/any-map-schema? nil) => false
  (kc/any-map-schema? {:a s/Str}) => false
  (kc/any-map-schema? s/Any) => true
  (kc/any-map-schema? {s/Keyword s/Any}) => true)

(p/defnk handler [[:data x :- s/Int] y :- s/Bool])

(fact "extracting schemas"
  (kc/extract-schema handler) => {:input {:data {:x s/Int, s/Keyword s/Any}, :y s/Bool, s/Keyword s/Any}
                                  :output s/Any})
