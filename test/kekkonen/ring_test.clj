(ns kekkonen.ring-test
  (:require [kekkonen.core :as k]
            [kekkonen.ring :as r]
            [midje.sweet :refer :all]
            [schema.core :as s]
            [plumbing.core :as p]))

; simplest thing that works
(p/defnk ^:handler ping [] "pong")

(p/defnk ^:handler snoop [] "pong")

(fact "defaut"
  (let [app (r/ring-handler
              (k/create {:handlers {:test 'kekkonen.ring-test}}))]

    (fact "non matching route returns nil"
      (app {:uri "/" :request-method :post}) => nil)

    (fact "matching route"
      (app {:uri "/test/ping" :request-method :post}) => "pong")))
