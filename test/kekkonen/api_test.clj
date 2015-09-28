(ns kekkonen.api-test
  (:require [midje.sweet :refer :all]
            [kekkonen.midje :refer :all]
            [ring.util.http-response :refer [ok]]
            [ring.util.http-predicates :refer [ok? not-found?]]
            [plumbing.core :as p]
            [kekkonen.core :as k]
            [kekkonen.api :refer [api]]))

(p/defnk ^:handler ping []
  (ok {:ping "pong"}))

(defn require-role [context role]
  (if (= (-> context :request :query-params ::role) role)
    context))

(facts "api-test"
  (let [secret (k/namespace {:name :secret ::role :admin})
        app (api {:core {:handlers {:api {:public #'ping
                                          secret #'ping}}
                         :user {::role require-role}}})]

    (facts "with access only to public handlers"

      (fact "public handler can be invoked"
        (let [response (app {:uri "/api/public/ping" :request-method :post})]
          response => ok?
          (parse response) => {:ping "pong"}))

      (fact "secret handler can't be invoked"
        (let [response (app {:uri "/api/secret/ping" :request-method :post})]
          response => not-found?
          (parse response) => nil))

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
                              {:/api/public/ping
                               {:post
                                {:parameters [{:in "header"
                                               :name "kekkonen.mode"
                                               :description "mode"
                                               :type "string"
                                               :enum ["invoke" "validate"]
                                               :default "invoke"
                                               :required false}]
                                 :responses {:default
                                             {:description ""}}
                                 :tags ["api.public"]}}})})

          (fact "there are extra (kekkonen) endpoints"
            body => (contains
                      {:paths
                       (just
                         {:/api/public/ping anything
                          ;:/kekkonen/actions anything
                          :/kekkonen/get-handler anything
                          :/kekkonen/all-handlers anything
                          :/kekkonen/available-handlers anything})})))))

    (fact "with access to secret handlers"

      (fact "public handler can be invoked"
        (let [response (app {:uri "/api/public/ping" :request-method :post :query-params {::role :admin}})]
          response => ok?
          (parse response) => {:ping "pong"}))

      (fact "secret handler can be invoked"
        (let [response (app {:uri "/api/secret/ping" :request-method :post :query-params {::role :admin}})]
          response => ok?
          (parse response) => {:ping "pong"}))

      (fact "swagger-object contains both publuc & secret routes"
        (let [response (app {:uri "/swagger.json" :request-method :get :query-params {::role :admin}})
              body (parse response)]
          response => ok?
          body => (contains
                    {:paths
                     (just
                       {:/api/public/ping anything
                        :/api/secret/ping anything
                        ;:/kekkonen/actions anything
                        :/kekkonen/get-handler anything
                        :/kekkonen/all-handlers anything
                        :/kekkonen/available-handlers anything})}))))

    (fact "swagger-ui"
      (let [response (app {:uri "/" :request-method :get})]
        response => (contains
                      {:status 302
                       :body ""
                       :headers (contains
                                  {"Location" "/index.html"})})))))
