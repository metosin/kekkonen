(ns kekkonen.api-test
  (:require [midje.sweet :refer :all]
            [kekkonen.midje :refer :all]
            [ring.util.http-response :refer [ok]]
            [ring.util.http-predicates :refer [ok?]]
            [plumbing.core :as p]
            [kekkonen.api :refer [api]]
            [schema.core :as s]))

(p/defnk ^:handler ping
  [[:data {x :- s/Int 1}]]
  (ok {:x x}))

(facts "api-test"
  (let [app (api {:core {:handlers {:api #'ping}}})]

    (fact "calling handlers"
      (let [response (app {:uri "/api/ping" :request-method :post})]
        response => ok?
        (parse response) => {:x 1}))

    (fact "swagger-object"
      (let [response (app {:uri "/swagger.json" :request-method :get})]
        response => ok?
        (parse response) => {:swagger "2.0"
                             :info {:title "Swagger API"
                                    :version "0.0.1"}
                             :consumes []
                             :produces []
                             :definitions {}
                             :paths {:/api/ping
                                     {:post
                                      {:responses
                                       {:default
                                        {:description ""}}
                                       :tags ["api"]}}}}))))
