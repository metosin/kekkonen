(ns kekkonen.ring-test
  (:require [kekkonen.core :as k]
            [kekkonen.ring :as r]
            [kekkonen.midje :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :refer [ok]]
            [plumbing.core :as p]
            [kekkonen.common :as kc]))

(facts "uri->action"
  (#'r/uri->action "/api/ipa/user/add-user!") => :api.ipa.user/add-user!
  (#'r/uri->action "/api") => :api
  (#'r/uri->action "/") => nil)

(fact "handler-uri"
  (#'r/handler-uri {:ns :api.user, :name :add-user!}) => "/api/user/add-user!"
  (#'r/handler-uri {:ns :api.user, :name :swagger.json}) => "/api/user/swagger.json"
  (#'r/handler-uri {:ns nil, :name :swagger.json}) => "/swagger.json")

(fact "ring-input-schema"
  (#'r/ring-input-schema
    {:data {:d s/Str}
     :request {:query-params {:q s/Str}
               :body-params {:b s/Str}}}
    {[:data] [:request :query-params]})
  => {:request {:query-params {:d s/Str}
                :body-params {:b s/Str}}})

(p/defnk ^:handler ping [] "pong")

(p/defnk ^:handler snoop [request] (ok request))

(facts "request routing"
  (let [app (r/ring-handler
              (k/dispatcher
                (kc/merge-map-like
                  r/+ring-dispatcher-options+
                  {:handlers {:api [#'ping #'snoop]}})))]

    (fact "non matching route returns nil"
      (app {:uri "/" :request-method :post}) => nil)

    (fact "matching route"
      (app {:uri "/api/ping" :request-method :post})
      => "pong")

    (fact "request can be read as-is"
      (let [request {:uri "/api/snoop" :request-method :post}]
        (app request) => (ok request)))

    (fact "handles request within context"
      (let [request {:uri "/somecontext/api/ping" :request-method :post :context "/somecontext"}]
        (app request) => "pong"))))

(p/defnk ^:handler plus
  [[:request [:query-params x :- s/Int, y :- s/Int]]]
  (ok (+ x y)))

(p/defnk ^:handler divide
  [[:request [:form-params x :- s/Int, y :- s/Int]]]
  (ok (/ x y)))

(p/defnk ^:handler power
  [[:request [:header-params x :- s/Int, y :- s/Int]]]
  (ok (long (Math/pow x y))))

(s/defschema Body {:name s/Str, :size (s/enum :S :M :L :XL)})

(p/defnk ^:handler echo
  [[:request body-params :- Body]]
  (ok body-params))

(p/defnk ^:handler response
  {:responses {200 {:schema {:value s/Str}}}}
  [[:request body-params :- {:value (s/either s/Str s/Int)}]]
  (ok body-params))

(p/defnk ^:handler response-default
  {:responses {:default {:schema {:value s/Str}}}}
  [[:request body-params :- {:value (s/either s/Str s/Int)}]]
  (ok body-params))

(fact "internal schemas"
  (s/with-fn-validation
    (r/ring-handler
      (k/dispatcher {:handlers {:api #'plus}}))))

(facts "coercion"
  (let [app (r/ring-handler
              (k/dispatcher
                (kc/merge-map-like
                  r/+ring-dispatcher-options+
                  {:handlers {:api [#'plus #'divide #'power #'echo #'response #'response-default]}})))]

    (fact "query-params"

      (fact "missing parameters"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "1"}})

        => (throws?
             {:type :kekkonen.ring/request
              :in :query-params
              :value {:x "1"}
              :schema {:x s/Int, :y s/Int s/Keyword s/Any}}))

      (fact "wrong parameter types"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "invalid" :y "2"}})

        => (throws?
             {:type :kekkonen.ring/request
              :in :query-params
              :value {:x "invalid" :y "2"}
              :schema {:x s/Int, :y s/Int s/Keyword s/Any}}))

      (fact "all good"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "1" :y "2"}}) => (ok 3)))

    (fact "form-params"
      (app {:uri "/api/divide"
            :request-method :post
            :form-params {:x "10" :y "2"}}) => (ok 5))

    (fact "header-params"
      (app {:uri "/api/power"
            :request-method :post
            :header-params {:x "2" :y "3"}}) => (ok 8))

    (fact "body-params"
      (app {:uri "/api/echo"
            :request-method :post
            :body-params {:name "Pizza" :size "L"}}) => (ok {:name "Pizza" :size :L}))

    (fact "response coercion"
      (fact "with status code"
        (app {:uri "/api/response"
              :request-method :post
              :body-params {:value "Pizza"}}) => (ok {:value "Pizza"})

        (app {:uri "/api/response"
              :request-method :post
              :body-params {:value 1}})

        => (throws?
             {:type :kekkonen.ring/response
              :in :response
              :value {:value 1}
              :schema {:value s/Str}}))

      (fact "with :default"
        (app {:uri "/api/response-default"
              :request-method :post
              :body-params {:value "Pizza"}}) => (ok {:value "Pizza"})

        (app {:uri "/api/response-default"
              :request-method :post
              :body-params {:value 1}})

        => (throws?
             {:type :kekkonen.ring/response
              :in :response
              :value {:value 1}
              :schema {:value s/Str}})))

    (fact "validation"

      (fact "missing parameters throws errors as expected"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "1"}
              :headers {"kekkonen.mode" "validate"}})

        => (throws?
             {:type :kekkonen.ring/request
              :in :query-params
              :value {:x "1"}
              :schema {:x s/Int, :y s/Int s/Keyword s/Any}}))

      (fact "all good returns ok nil"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "1" :y "2"}
              :headers {"kekkonen.mode" "validate"}}) => (ok nil)))))

(facts "no coercion"

  (fact "any ring coercion can be changed"
    (let [app (r/ring-handler
                (k/dispatcher {:handlers {:api [#'plus]}})
                {:coercion {:query-params (get-in r/+default-options+ [:coercion :body-params])}})]

      (app {:uri "/api/plus"
            :request-method :post
            :query-params {:x "1", :y "2"}}) => (throws? {:type :kekkonen.ring/request})))

  (fact "any ring coercion can be disabled"
    (fact "if handler is dependent on :request-input, no coercion is done"
      (let [app (r/ring-handler
                  (k/dispatcher {:handlers {:api [#'plus]}})
                  {:coercion {:query-params nil}})]

        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "1", :y "2"}}) => (throws-interceptor-exception?
                                                    {:exception-type :java.lang.ClassCastException})))
    (fact "if handler is dependent on :data-input, default coercion is applies"
      (let [app (r/ring-handler
                  (k/dispatcher {:handlers {:api (k/handler
                                                   {:name :plus
                                                    :handle (p/fnk [[:data x :- s/Int, y :- s/Int]]
                                                              (ok (+ x y)))})}})
                  {:coercion {:body-params nil}})]

        (app {:uri "/api/plus"
              :request-method :post
              :body-params {:x "1", :y "2"}}) => (throws? {:type :kekkonen.core/request}))))

  (fact "all ring coercions can be disabled"
    (let [app (r/ring-handler
                (k/dispatcher {:handlers {:api [#'plus]}})
                {:coercion nil})]

      (app {:uri "/api/plus"
            :request-method :post
            :query-params {:x "1", :y "2"}}) => (throws-interceptor-exception?
                                                  {:exception-type :java.lang.ClassCastException})))

  (fact "all ring & core coercions can be disabled"
    (let [app (r/ring-handler
                (k/dispatcher {:handlers {:api [#'plus]}, :coercion nil})
                {:coercion nil})]

      (app {:uri "/api/plus"
            :request-method :post
            :query-params {:x "1", :y "2"}}) => (throws-interceptor-exception?
                                                  {:exception-type :java.lang.ClassCastException}))))

(facts "mapping"
  (facts "default body-params -> data"
    (let [app (r/ring-handler
                (k/dispatcher {:handlers {:api (k/handler {:name :test, :handle identity})}}))]

      (app {:uri "/api/test"
            :request-method :post
            :body-params {:kikka "kukka"}}) => (contains {:data {:kikka "kukka"}})))

  (fact "custom query-params -> query via interceptor"
    (let [app (r/ring-handler
                (k/dispatcher {:handlers {:api (k/handler {:name :test, :handle identity})}})
                {:interceptors [(k/context-copy [:request :query-params] [:query])]})]

      (app {:uri "/api/test"
            :request-method :post
            :query-params {:kikka "kukka"}}) => (contains {:query {:kikka "kukka"}})))

  (fact "custom query-params -> query via parameters"
    (let [app (r/ring-handler
                (k/dispatcher {:handlers {:api (k/handler {:name :test, :handle identity})}})
                {:types {:handler {:parameters {[:query] [:request :query-params]}}}})]

      (app {:uri "/api/test"
            :request-method :post
            :query-params {:kikka "kukka"}}) => (contains {:query {:kikka "kukka"}}))))

(facts "routing"
  (let [app (r/routes [(r/match "/swagger.json" #{:get} (constantly :swagger))
                       (r/match "/api-docs" (constantly :api-docs))])]

    (app {:uri "/swagger.json" :request-method :get}) => :swagger
    (app {:uri "/swagger.json" :request-method :post}) => nil
    (app {:uri "/api-docs" :request-method :head}) => :api-docs
    (app {:uri "/favicon.ico" :request-method :get}) => nil))

(fact "enriched handlers"
  (let [app (r/ring-handler
              (k/dispatcher
                {:handlers
                 {:api
                  (k/handler
                    {:name :test
                     :handle (partial k/get-handler)})}}))]

    (app {:uri "/api/test" :request-method :post}) => (contains
                                                        {:ring
                                                         (contains
                                                           {:type-config
                                                            (contains
                                                              {:methods #{:post}})})})))

(fact "interceptors"
  (let [app (r/ring-handler
              (k/dispatcher
                {:handlers
                 {:api
                  (k/handler
                    {:name :test
                     :handle (fn [context]
                               {:user (-> context ::user)})})}})
              {:interceptors [{:enter (fn [ctx]
                                        (let [user (get-in ctx [:request :header-params "user"])]
                                          (assoc ctx ::user user)))
                               :leave (fn [ctx]
                                        (assoc-in ctx [:response :leave1] true))}
                              {:enter (fn [context]
                                        (if (::user context)
                                          (update context ::user #(str % "!"))
                                          context))
                               :leave (fn [ctx]
                                        (assoc-in ctx [:response :leave2] true))}]})]

    (app {:uri "/api/test"
          :request-method :post}) => {:user nil, :leave1 true, :leave2 true}

    (app {:uri "/api/test"
          :request-method :post
          :header-params {"user" "tommi"}}) => {:user "tommi!", :leave1 true, :leave2 true}))

(fact "dispatcher context is available for ring interceptors, fixes #26"
  (let [app (r/ring-handler
              (k/dispatcher
                {:context {:secret 42}
                 :handlers {:api (k/handler {:name :ipa, :handle (fn [ctx] (::value ctx))})}})
              {:interceptors [(fn [ctx] (assoc ctx ::value (:secret ctx)))]})]
    (app {:uri "/api/ipa" :request-method :post}) => 42))
