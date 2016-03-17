(ns example.cqrs
  (:require [org.httpkit.server :as server]
            [kekkonen.cqrs :refer :all]
            [plumbing.core :refer [defnk]]
            [schema.core :as s]
            [clojure.set :as set]))

;;
;; Security
;;

(s/defschema User
  {:name s/Str
   :roles #{s/Keyword}})

(defn api-key-authenticator [context]
  (let [api-key (-> context :request :query-params :api_key)
        user (condp = api-key
               "seppo" {:name "Seppo" :roles #{}}
               "sirpa" {:name "Sirpa" :roles #{:boss}}
               nil)]
    (assoc context :user user)))

(defn require-roles [required]
  (fn [context]
    (let [roles (-> context :user :roles)]
      (if (seq (set/intersection roles required))
        context))))

(defnk ^:query get-user
  {:responses {:default {:schema (s/maybe User)}}}
  [user] (success user))

;;
;; Schemas
;;

(s/defschema Item
  "A database item"
  {:id s/Int
   :name s/Str
   :size (s/enum :S :M :L)
   (s/optional-key :description) s/Str
   :origin {:country (s/enum :FI :PO)}})

(s/defschema AddNewItem
  (dissoc Item :id))

;;
;; Commands & Queries
;;

(defnk ^:query get-items
  "Retrieves all items"
  {:responses {:default {:schema [Item]}}}
  [[:components db]]
  (success (vals @db)))

(defnk ^:command add-item
  "Adds an item to database"
  {:responses {:default {:schema Item}}}
  [[:components db ids]
   data :- AddNewItem]
  (let [id (swap! ids inc)
        item (assoc data :id id)]
    (success
      (get (swap! db assoc id item) id))))


(defnk ^:command reset-items
  "Resets the database"
  {::roles #{:boss}}
  [[:components db]
   [:data really :- s/Bool]]
  (success
    (if really
      (swap! db empty)
      @db)))

(defnk ^:query ping [] (success {:ping "pong"}))
(defnk ^:query pong [] (success {:pong "ping"}))

;;
;; parameters
;;

(defnk ^:query plus
  {:responses {:default {:schema {:result s/Int}}}}
  [[:data x :- s/Int, y :- s/Int]]
  (success {:result (+ x y)}))

(defnk ^:query times
  {:responses {:default {:schema {:result s/Int}}}}
  [[:data x :- s/Int, y :- s/Int]]
  (success {:result (* x y)}))

(defnk ^:command increment
  {:responses {:default {:schema {:result s/Int}}}}
  [[:components counter]]
  (success {:result (swap! counter (partial + 10))}))

;;
;; Application
;;

(def app
  (cqrs-api
    {:swagger {:info {:title "Kekkonen"}}
     :core {:handlers {:api {:item [#'get-items #'add-item #'reset-items]
                             :calculator [#'plus #'times #'increment]
                             :security #'get-user
                             :system [#'ping #'pong]}}
            :context {:components {:db (atom {})
                                   :ids (atom 0)
                                   :counter (atom 0)}}
            :meta {::roles require-roles}}
     :ring {:interceptors [api-key-authenticator]}}))

(comment
  (server/run-server #'app {:port 3000}))
