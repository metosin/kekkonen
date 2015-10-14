(ns kekkonen.api-test
  (:require [midje.sweet :refer :all]
            [kekkonen.midje :refer :all]
            [ring.util.http-response :refer [ok]]
            [ring.util.http-predicates :refer [ok? not-found? bad-request?]]
            [plumbing.core :as p]
            [kekkonen.core :as k]
            [kekkonen.api :refer [api]]
            [schema.core :as s]))

(p/defnk ^:handler plus [[:data x :- s/Int]]
  (ok {:result (inc x)}))

(defn require-role [role]
  (fn [context]
    (if (= (-> context :request :query-params ::role) role)
      context)))

(facts "api-test"
  (let [secret (k/namespace {:name :secret ::role :admin})
        app (api {:core {:handlers {:api {:public #'plus
                                          secret [#'plus]}}
                         :user {::role require-role}}})]

    (facts "without required roles"

      (fact "invalid handler"
        (fact "can't be validated"
          (let [response (app {:uri "/INVALID"
                               :request-method :post
                               :headers {"kekkonen.mode" "validate"}})]
            response => not-found?
            (parse response) => nil))

        (fact "can't be invoked"
          (let [response (app {:uri "/INVALID"
                               :request-method :post})]
            response => not-found?
            (parse response) => nil)))

      (fact "public handler"
        (fact "can be validated"
          (fact "with valid parameers"
            (let [response (app {:uri "/api/public/plus"
                                 :request-method :post
                                 :headers {"kekkonen.mode" "validate"}
                                 :body-params {:x 1}})]
              response => ok?
              (parse response) => nil))

          (fact "with invalid parameters"
            (let [response (app {:uri "/api/public/plus"
                                 :request-method :post
                                 :headers {"kekkonen.mode" "validate"}})]
              response => bad-request?
              (parse response) => {:error {:x "missing-required-key"}
                                   :in "body-params"
                                   :type "kekkonen.ring/request"
                                   :value {}})))

        (fact "can be invoked"
          (fact "with valid parameers"
            (let [response (app {:uri "/api/public/plus"
                                 :request-method :post
                                 :body-params {:x 1}})]
              response => ok?
              (parse response) => {:result 2}))

          (fact "with invalid parameters"
            (let [response (app {:uri "/api/public/plus"
                                 :request-method :post})]
              response => bad-request?
              (parse response) => {:error {:x "missing-required-key"}
                                   :in "body-params"
                                   :type "kekkonen.ring/request"
                                   :value {}}))))

      (fact "secret handler"
        (fact "without role"
          (fact "can't be validated"
            (let [response (app {:uri "/api/secret/nada"
                                 :request-method :post
                                 :headers {"kekkonen.mode" "validate"}})]
              response => not-found?
              (parse response) => nil))

          (fact "can't be validated"
            (let [response (app {:uri "/api/secret/plus"
                                 :request-method :post
                                 :headers {"kekkonen.mode" "validate"}})]
              response => not-found?
              (parse response) => nil))

          (fact "can't be invoked"
            (let [response (app {:uri "/api/secret/plus"
                                 :request-method :post})]
              response => not-found?
              (parse response) => nil)))

        (fact "with role"
          (fact "can be validated"
            (fact "with valid parameers"
              (let [response (app {:uri "/api/secret/plus"
                                   :request-method :post
                                   :headers {"kekkonen.mode" "validate"}
                                   :query-params {::role :admin}
                                   :body-params {:x 1}})]
                response => ok?
                (parse response) => nil))

            (fact "with invalid parameters"
              (let [response (app {:uri "/api/secret/plus"
                                   :request-method :post
                                   :headers {"kekkonen.mode" "validate"}
                                   :query-params {::role :admin}})]
                response => bad-request?
                (parse response) => {:error {:x "missing-required-key"}
                                     :in "body-params"
                                     :type "kekkonen.ring/request"
                                     :value {}})))

          (fact "can be invoked"
            (fact "with valid parameers"
              (let [response (app {:uri "/api/secret/plus"
                                   :request-method :post
                                   :query-params {::role :admin}
                                   :body-params {:x 1}})]
                response => ok?
                (parse response) => {:result 2}))

            (fact "with invalid parameters"
              (let [response (app {:uri "/api/secret/plus"
                                   :request-method :post
                                   :query-params {::role :admin}})]
                response => bad-request?
                (parse response) => {:error {:x "missing-required-key"}
                                     :in "body-params"
                                     :type "kekkonen.ring/request"
                                     :value {}})))))

      (fact "swagger-object"
        (fact "without role"
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
                                {:/api/public/plus
                                 (just
                                   {:post
                                    (just
                                      {:parameters
                                       (just
                                         [(just
                                            {:in "body"
                                             :name anything
                                             :description ""
                                             :required true
                                             :schema (just {:$ref anything})})
                                          (just
                                            {:in "header"
                                             :name "kekkonen.mode"
                                             :description "mode"
                                             :type "string"
                                             :enum ["invoke" "validate"]
                                             :default "invoke"
                                             :required false})] :in-any-order)
                                       :responses {:default
                                                   {:description ""}}
                                       :tags ["api.public"]})})})})

            (fact "there are extra (kekkonen) endpoints"
              body => (contains
                        {:paths
                         (just
                           {:/api/public/plus anything
                            ;:/kekkonen/actions anything
                            :/kekkonen/get-handler anything
                            :/kekkonen/all-handlers anything
                            :/kekkonen/available-handlers anything})}))))

        (fact "with role"
          (let [response (app {:uri "/swagger.json" :request-method :get :query-params {::role :admin}})
                body (parse response)]
            response => ok?

            (fact "secret endpoints are also documented"
              body => (contains
                        {:paths
                         (contains
                           {:/api/public/plus anything
                            :/api/secret/plus anything})})))))

      (fact "swagger-ui"
        (let [response (app {:uri "/" :request-method :get})]
          response => (contains
                        {:status 302
                         :body ""
                         :headers (contains
                                    {"Location" "/index.html"})}))))))
