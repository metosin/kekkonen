(ns kekkonen.http-test
  (:require [midje.sweet :refer :all]
            [ring.util.http-response :refer [ok]]
            [plumbing.core :as p]
            [kekkonen.ring :as r]
            [kekkonen.http :as h]
            [kekkonen.core :as k]))

(p/defnk ^:get     get-it [] (ok))
(p/defnk ^:head    head-it [] (ok))
(p/defnk ^:patch   patch-it [] (ok))
(p/defnk ^:delete  delete-it [] (ok))
(p/defnk ^:options options-it [] (ok))
(p/defnk ^:post    post-it [] (ok))
(p/defnk ^:put     put-it [] (ok))
(p/defnk ^:any     any-it [] (ok))

(facts "web-options"
  (let [app (r/ring-handler
              (k/create
                {:handlers {:api [#'get-it #'head-it #'patch-it #'delete-it #'options-it #'post-it #'put-it #'any-it]}
                 :type-resolver h/+http-type-resolver+})
              {:types h/+http-types+})]

    (fact "get"     (app {:uri "/api/get-it",     :request-method :get})     => (ok))
    (fact "head"    (app {:uri "/api/head-it",    :request-method :head})    => (ok))
    (fact "patch"   (app {:uri "/api/patch-it",   :request-method :patch})   => (ok))
    (fact "delete"  (app {:uri "/api/delete-it",  :request-method :delete})  => (ok))
    (fact "options" (app {:uri "/api/options-it", :request-method :options}) => (ok))
    (fact "post"    (app {:uri "/api/post-it",    :request-method :post})    => (ok))
    (fact "put"     (app {:uri "/api/put-it",     :request-method :put})     => (ok))

    (fact "any"
      (app {:uri "/api/any-it", :request-method :get})     => (ok)
      (app {:uri "/api/any-it", :request-method :head})    => (ok)
      (app {:uri "/api/any-it", :request-method :patch})   => (ok)
      (app {:uri "/api/any-it", :request-method :delete})  => (ok)
      (app {:uri "/api/any-it", :request-method :options}) => (ok)
      (app {:uri "/api/any-it", :request-method :post})    => (ok)
      (app {:uri "/api/any-it", :request-method :put})     => (ok))))
