(ns kekkonen.ring-test
  (:require [kekkonen.core :as k]
            [kekkonen.ring :as r]
            [midje.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :refer [ok]]
            [plumbing.core :as p]))

(facts "uris, actions, handlers"
  (r/uri->action "/api/user/add-user!") => :api/user/add-user!
  (r/handler-uri {:ns :api/user, :name :add-user!}) => "/api/user/add-user!")

(p/defnk ^:handler ping [] "pong")

(p/defnk ^:handler snoop [request] (ok request))

(facts "request routing"
  (let [app (r/ring-handler
              (k/create {:handlers {:api [#'ping #'snoop]}}))]

    (fact "non matching route returns nil"
      (app {:uri "/" :request-method :post}) => nil)

    (fact "matching route"
      (app {:uri "/api/ping" :request-method :post})
      => "pong")

    (fact "request can be read as-is"
      (let [request {:uri "/api/snoop" :request-method :post}]
        (app request) => (ok request)))))

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

(p/defnk ^:handler responsez
  {:responses {200 {:schema {:value s/Str}}}}
  [[:request body-params :- {:value (s/either s/Str s/Int)}]]
  (ok body-params))

(facts "coercion"
  (let [app (r/ring-handler
              (k/create {:handlers {:api [#'plus #'divide #'power #'echo #'responsez]}}))]

    (fact "query-params"

      (fact "missing parameters"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "1"}})) => (throws RuntimeException)

      (fact "wrong parameter types"
        (app {:uri "/api/plus"
              :request-method :post
              :query-params {:x "invalid" :y "2"}}) => (throws RuntimeException))

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
      (app {:uri "/api/responsez"
            :request-method :post
            :body-params {:value "Pizza"}}) => (ok {:value "Pizza"})
      (app {:uri "/api/responsez"
            :request-method :post
            :body-params {:value 1}}) => (throws RuntimeException))))

(p/defnk ^:get get-it [] (ok))
(p/defnk ^:head head-it [] (ok))
(p/defnk ^:patch patch-it [] (ok))
(p/defnk ^:delete delete-it [] (ok))
(p/defnk ^:options options-it [] (ok))
(p/defnk ^:post post-it [] (ok))
(p/defnk ^:put put-it [] (ok))
(p/defnk ^:any any-it [] (ok))

(facts "web-options"
  (let [app (r/ring-handler
              (k/create
                {:handlers {:api [#'get-it #'head-it #'patch-it #'delete-it #'options-it #'post-it #'put-it #'any-it]}
                 :type-resolver r/http-type-resolver})
              {:types r/http-types})]

    (fact "get"     (app {:uri "/api/get-it", :request-method :get}) => (ok))
    (fact "head"    (app {:uri "/api/head-it", :request-method :head}) => (ok))
    (fact "patch"   (app {:uri "/api/patch-it", :request-method :patch}) => (ok))
    (fact "delete"  (app {:uri "/api/delete-it", :request-method :delete}) => (ok))
    (fact "options" (app {:uri "/api/options-it", :request-method :options}) => (ok))
    (fact "post"    (app {:uri "/api/post-it", :request-method :post}) => (ok))
    (fact "put"     (app {:uri "/api/put-it", :request-method :put}) => (ok))

    (fact "any"
      (app {:uri "/api/any-it", :request-method :get}) => (ok)
      (app {:uri "/api/any-it", :request-method :head}) => (ok)
      (app {:uri "/api/any-it", :request-method :patch}) => (ok)
      (app {:uri "/api/any-it", :request-method :delete}) => (ok)
      (app {:uri "/api/any-it", :request-method :options}) => (ok)
      (app {:uri "/api/any-it", :request-method :post}) => (ok)
      (app {:uri "/api/any-it", :request-method :put}) => (ok))))
