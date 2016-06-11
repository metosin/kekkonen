(ns get-post.core-test
  (:use midje.sweet)
  (:require [clojure.test :refer :all]
            [get-post.core :refer :all]
            [ring.mock.request :as mock]
            [kekkonen.cqrs :as cqrs]))

(facts "About http handling"
       (fact "GET works for get-and-post"
             (app (mock/request :get "/api/get-and-post?name=taras")) => cqrs/success?)
       (fact "POST works for get-and-post"
             (let [r (mock/request :post "/api/get-and-post")
                   rb (assoc r :body-params {:data "taras"})]
               (app rb) => cqrs/success?)))