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

(p/defnk ^:handler nada [] (ok))

(defn require-role [role]
  (fn [context]
    (if (= (-> context :request :query-params ::role) role)
      context)))

(facts "api-test"
  (let [secret (k/namespace {:name :secret ::role :admin})
        app (api {:core {:handlers {:api {:public [#'plus #'nada]
                                          secret #'plus}}
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
                                   :value {}})))))

    (fact "secret handler"
      (fact "without role"
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

    (fact "kekkonen endpoints"
      (fact "get-handler"
        (let [response (app {:uri "/kekkonen/handler"
                             :request-method :post
                             :body-params {:action "api.public/plus"}})]
          response => ok?
          (parse response) => (contains
                                {:action "api.public/plus"})))

      (fact "available-handlers"
        (fact "without role"
          (let [response (app {:uri "/kekkonen/handlers"
                               :request-method :post})]
            response => ok?
            (parse response) => (just [(contains {:action "api.public/plus"})
                                       (contains {:action "api.public/nada"})] :in-any-order)))

        (fact "with role"
          (let [response (app {:uri "/kekkonen/handlers"
                               :query-params {::role :admin}
                               :request-method :post})]
            response => ok?
            (parse response) => (just [(contains {:action "api.public/plus"})
                                       (contains {:action "api.public/nada"})
                                       (contains {:action "api.secret/plus"})] :in-any-order))))

      (fact "actions"
        (fact "without role"
          (let [response (app {:uri "/kekkonen/actions"
                               :request-method :post})]
            response => ok?
            (parse response) => {:api.public/plus nil
                                 :api.public/nada nil}))

        (fact "with role"
          (let [response (app {:uri "/kekkonen/actions"
                               :query-params {::role :admin}
                               :request-method :post})]
            response => ok?
            (parse response) => {:api.public/plus nil
                                 :api.public/nada nil
                                 :api.secret/plus nil})

          (fact "mode = check"
            (let [response (app {:uri "/kekkonen/actions"
                                 :query-params {::role :admin}
                                 :body-params {:mode :check}
                                 :request-method :post})]
              response => ok?
              (parse response) => {:api.public/plus nil
                                   :api.public/nada nil
                                   :api.secret/plus nil}))

          (fact "mode = validate"
            (let [response (app {:uri "/kekkonen/actions"
                                 :query-params {::role :admin}
                                 :body-params {:mode :validate}
                                 :request-method :post})]
              response => ok?
              (parse response) => (just
                                    {:api.public/plus map?
                                     :api.public/nada nil
                                     :api.secret/plus map?})))

          (fact "invalid mode"
            (let [response (app {:uri "/kekkonen/actions"
                                 :query-params {::role :admin}
                                 :body-params {:mode :INVALID}
                                 :request-method :post})]
              response => bad-request?
              (parse response) => map?)))))

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
                          :/api/public/nada anything
                          :/kekkonen/handler anything
                          :/kekkonen/handlers anything
                          :/kekkonen/actions anything})}))

          (fact "secret endpoints are not documented"
            body =not=> (contains
                          {:paths
                           (contains
                             {:/api/secret/plus anything})})))

        (fact "with ns-filter"
          (let [response (app {:uri "/swagger.json"
                               :request-method :get
                               :query-params {::role :admin
                                              :ns "api.public"}})
                body (parse response)]
            response => ok?
            body => (contains
                      {:paths
                       (just
                         {:/api/public/plus anything
                          :/api/public/nada anything})})))))

    (fact "with role"
      (let [response (app {:uri "/swagger.json"
                           :request-method :get
                           :query-params {::role :admin}})
            body (parse response)]
        response => ok?

        (fact "secret endpoints are also documented"
          body => (contains
                    {:paths
                     (contains
                       {:/api/secret/plus anything})}))))

    (fact "swagger-ui"
      (let [response (app {:uri "/" :request-method :get})]
        response => (contains
                      {:status 302
                       :body ""
                       :headers (contains
                                  {"Location" "/index.html"})})))))
