(ns kekkonen.api-test
  (:require [midje.sweet :refer :all]
            [kekkonen.midje :refer :all]
            [ring.util.http-response :refer [ok]]
            [ring.util.http-predicates :refer [ok?]]
            [plumbing.core :as p]
            [kekkonen.api :refer [api]]))

(p/defnk ^:handler ping []
  (ok {:ping "pong"}))

(facts "api-test"
  (let [app (api {:core {:handlers {:api #'ping}}})]

    (fact "calling handlers"
      (let [response (app {:uri "/api/ping" :request-method :post})]
        response => ok?
        (parse response) => {:ping "pong"}))

    (fact "swagger-object"
      (let [response (app {:uri "/swagger.json" :request-method :get})
            body (parse response)]
        response => ok?
        body => (contains
                  {:swagger "2.0"
                   :info {:title "Swagger API"
                          :version "0.0.1"}
                   :consumes ["application/json"
                              "application/x-yaml"
                              "application/edn"
                              "application/transit+json"
                              "application/transit+msgpack"]
                   :produces ["application/json"
                              "application/x-yaml"
                              "application/edn"
                              "application/transit+json"
                              "application/transit+msgpack"]
                   :definitions anything
                   :paths (contains
                            {:/api/ping
                             {:post
                              {:parameters [{:in "header"
                                             :name "kekkonen.mode"
                                             :description "mode"
                                             :type "string"
                                             :enum ["invoke" "validate"]
                                             :default "invoke"
                                             :required false}]
                               :responses
                               {:default
                                {:description ""}}
                               :tags ["api"]}}})})

        (fact "there are 3 extra (kekkonen) endpoints"
          body => (contains {:paths (n-of anything 3)}))))

    (fact "swagger-ui"
      (let [response (app {:uri "/" :request-method :get})]
        response => (contains
                      {:status 302
                       :body ""
                       :headers (contains
                                  {"Location" "/index.html"})})))))
