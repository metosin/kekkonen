(ns kekkonen.cqrs-test
  (:require [kekkonen.core :as k]
            [kekkonen.ring :as r]
            [kekkonen.cqrs :as cqrs :refer :all]
            [kekkonen.midje :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :refer [ok]]
            [plumbing.core :as p]))

(p/defnk ^:query get-items :- #{s/Str}
  "Retrieves all"
  [[:components db]]
  (success @db))

(p/defnk ^:command add-item! :- #{s/Str}
  "Adds an item to database"
  [[:data item :- String]
   [:components db]]
  (success (swap! db conj item)))

(p/defnk ^:command reset-items! :- #{s/Str}
  "Resets the database"
  [[:components db]]
  (success (swap! db empty)))

; TODO: test the api, not the internals
#_(facts "commands and querys"
  (let [kekkonen (k/create
                   {:context {:components {:db (atom #{})}}
                    :handlers {:api {:items [#'get-items #'add-item! #'reset-items!]}}
                    :type-resolver cqrs/+cqrs-type-resolver+})
        app (r/ring-handler
              kekkonen
              {:types cqrs/+cqrs-types+})]

    (fact "get-items"
      (k/invoke kekkonen :api/items/get-items) => (success #{})
      (app {:uri "/api/items/get-items", :request-method :get}) => (success #{}))

    (fact "add-item!"
      (k/invoke kekkonen :api/items/add-item! {:data {:item "kikka"}}) => (success #{"kikka"})
      (app {:uri "/api/items/add-item!"
            :request-method :post
            :body-params {:item "kikka"}}) => (success #{"kikka"}))))

(fact "statuses"

  success-status => 200
  failure-status => 400
  error-status => 500

  (success) => (contains {:status success-status})
  (failure) => (contains {:status failure-status})
  (error) => (contains {:status error-status})

  (failure!) => (throws? {:type :ring.util.http-response/response})
  (error!) => (throws? {:type :ring.util.http-response/response})

  (success) => success?
  (failure) => failure?
  (error) => error?)
