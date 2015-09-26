(ns kekkonen.cqrs-test
  (:require [kekkonen.cqrs :refer :all]
            [kekkonen.midje :refer :all]
            [midje.sweet :refer :all]
            [ring.util.http-response :refer [ok]]
            [ring.util.http-predicates :as http-predicates]
            [plumbing.core :as p]
            [clojure.set :as set]))

;;
;; Role interceptor
;;

(defn require-roles [context required]
  (let [roles (-> context :user :roles)]
    (if (seq (set/intersection roles required))
      context
      (failure! {:code "Missing role"
                 :roles roles
                 :required required}))))


(p/defnk ^:query get-items
  "Retrieves all"
  [[:components db]]
  (success @db))

(p/defnk ^:command add-item!
  "Adds an item to database"
  [[:data item :- String]
   [:components db]]
  (success (swap! db conj item)))

(p/defnk ^:command reset-items!
  "Resets the database"
  {::roles #{:admin}}
  [[:components db]]
  (success (swap! db empty)))

(facts "response codes"
  (success) => http-predicates/ok?
  (failure) => http-predicates/bad-request?
  (error) => http-predicates/internal-server-error?)

(facts "commands & queries"
  (meta (command {:name 'kikka} identity)) => (contains {:type :command})
  (meta (query {:name 'kikka} identity)) => (contains {:type :query}))

(facts "cqrs-api"
  (let [app (cqrs-api
              {:core
               {:context {:components {:db (atom #{})}}
                :handlers {:api {:items [#'get-items
                                         #'add-item!]
                                 :items2 #'reset-items!}}
                :user {::roles require-roles}}})]

    (fact "get-items"
      (let [response (app {:uri "/api/items/get-items"
                           :request-method :get})]
        response => success?
        (parse response) => []))

    (fact "add-item!"
      (let [response (app {:uri "/api/items/add-item!"
                           :request-method :post
                           :body-params {:item "kikka"}})]
        response => success?
        (parse response) => ["kikka"]))

    (fact "kekkonen endpoints"

      (fact "get-all returns info of all handlers"
        (let [response (app {:uri "/kekkonen/get-all"
                             :request-method :get})]
          response => success?
          (parse response) => (n-of map? 3)))

      (fact "get-all with ns returns all handlers in that ns"
        (let [response (app {:uri "/kekkonen/get-all"
                             :request-method :get
                             :query-params {:ns "api.items"}})]
          response => success?
          (parse response) => (n-of map? 2)))

      (fact "get-all with invalid ns returns nothing"
        (let [response (app {:uri "/kekkonen/get-all"
                             :request-method :get
                             :query-params {:ns "api.item"}})]
          response => success?
          (parse response) => (n-of map? 0)))

      (fact "get-available returns all handlers with rules ok"
        (let [response (app {:uri "/kekkonen/get-available"
                             :request-method :get})]
          response => success?
          (parse response) => (n-of map? 2)))

      (fact "get-available with ns returns all handlers with rules ok"
        (let [response (app {:uri "/kekkonen/get-available"
                             :request-method :get
                             :query-params {:ns "api.items"}})]
          response => success?
          (parse response) => (n-of map? 2)))

      (fact "get-available with invalid ns returns nothing"
        (let [response (app {:uri "/kekkonen/get-available"
                             :request-method :get
                             :query-params {:ns "api.item"}})]
          response => success?
          (parse response) => (n-of map? 0)))

      (fact "get-handler with valid handler action"
        (let [response (app {:uri "/kekkonen/get-handler"
                             :request-method :get
                             :query-params {:action "api.items/get-items"}})]
          response => success?
          (parse response) => map?))

      (fact "get-handler with invalid handler action returns nil"
        (let [response (app {:uri "/kekkonen/get-handler"
                             :request-method :get
                             :query-params {:action "api.items/get-item"}})]
          response => success?
          (parse response) => nil)))))

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
