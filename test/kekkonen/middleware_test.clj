(ns kekkonen.middleware-test
  (:require [kekkonen.core :as k]
            [kekkonen.ring :as r]
            [kekkonen.middleware :as mw]
            [kekkonen.midje :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :refer [ok]]
            [ring.util.http-predicates :as hp]
            [plumbing.core :as p]
            [kekkonen.common :as kc]
            [muuntaja.core :as muuntaja]
            [muuntaja.options :as options]))

(p/defnk ^:handler plus
  [[:request [:query-params x :- s/Int, y :- s/Int]]]
  (ok (+ x y)))

(p/defnk ^:handler responsez
  {:responses {200 {:schema {:value s/Str}}}}
  [[:request body-params :- {:value (s/either s/Str s/Int)}]]
  (ok body-params))

(facts "wrap-exceptions"
  (let [app (mw/wrap-exceptions
              (r/ring-handler
                (k/dispatcher
                  (kc/merge-map-like
                    r/+ring-dispatcher-options+
                    {:handlers {:api [#'plus #'responsez]}})))
              (:exceptions mw/+default-options+))]

    (fact "request coercion errors"
      (let [response (app {:uri "/api/plus"
                           :request-method :post
                           :query-params {:x "1"}})]

        response => hp/bad-request?
        (:body response) => {:error {:y "missing-required-key"}
                             :in :query-params
                             :type :kekkonen.ring/request
                             :value {:x "1"}}))

    (fact "response coercion errors"
      (let [response (app {:uri "/api/responsez"
                           :request-method :post
                           :body-params {:value 1}})]

        response => hp/internal-server-error?
        (:body response) => {:error {:value "(not (instance? java.lang.String 1))"}
                             :in :response
                             :type :kekkonen.ring/response
                             :value {:value 1}}))))

(facts "api-info"
  (let [options {:formats (muuntaja/create
                            (options/formats
                              muuntaja/default-options
                              ["application/json"
                               "application/transit+json"
                               "application/edn"]))}]
    (mw/api-info options) => {:consumes #{"application/json"
                                          "application/transit+json"
                                          "application/edn"}
                              :produces #{"application/json"
                                          "application/transit+json"
                                          "application/edn"}}))

(facts "wrap-keyword-keys"
  ((mw/wrap-keyword-keys identity [:a :b]) {:a {:b {"kissa" "koira", "banaani" "valas"}}})
  => {:a {:b {:kissa "koira", :banaani "valas"}}})
