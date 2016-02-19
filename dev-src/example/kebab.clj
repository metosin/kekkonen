(ns example.kebab
  (:require [org.httpkit.server :as server]
            [kekkonen.cqrs :refer :all]
            [plumbing.core :refer [defnk fnk]]
            [example.security :as security]
            example.math
            [schema.core :as s]
            [kekkonen.core :as k]))

;;
;; Tx
;;

(defn- forward [dispatcher action ctx data]
  (try
    (k/invoke dispatcher action (assoc ctx :data data))
    (catch Exception e
      (ex-data e))))

(defnk ^:command speculative
  "Dummy implementation. In real life, use a real TX system such as the RDB"
  {:summary "Runs a speculative transaction."}
  [[:system db ids]
   [:data action :- s/Keyword, data :- s/Any]
   :as ctx]

  (let [db' (atom @db)
        ids' (atom @ids)
        ctx' (assoc ctx :system {:db db', :ids ids'})

        dispatcher (k/get-dispatcher ctx)
        response (forward dispatcher action ctx' data)]

    response))

(defnk ^:command transact
  "Dummy implementation. In real life, use a real TX system such as the RDB"
  {:summary "Runs multiple commands in a single transaction."}
  [[:system db ids]
   [:data commands :- [{:action s/Keyword
                        :data s/Any}]] :as ctx]

  (let [db' (atom @db)
        ids' (atom @ids)
        ctx' (assoc ctx :system {:db db', :ids ids'})

        dispatcher (k/get-dispatcher ctx)
        responses (map
                    (fnk [action data]
                      (forward dispatcher action ctx' data))
                    commands)

        {successed true, failed false} (group-by success? responses)
        should-commit? (not (seq failed))
        response (if should-commit? success failure)]

    (when should-commit?
      (reset! db @db')
      (reset! ids @ids'))

    (response {:success successed
               :failed failed})))

;;
;; Schemas
;;

(s/defschema Kebab
  {:id s/Int
   :name s/Str
   :type (s/enum :doner :sish :souvlaki :mustamakkara)})

(s/defschema NewKebab
  (dissoc Kebab :id))

;;
;; Commands & Queries
;;

(defnk ^:query get-kebabs
  "Retrieves all kebabs"
  {:responses {:default {:schema [Kebab]}}}
  [[:system db]]
  (success (vals @db)))

(defnk ^:command add-kebab
  "Adds an kebab to database"
  {:responses {:default {:schema Kebab}}}
  [[:system db ids]
   data :- NewKebab]

  (if (-> data :type (= :mustamakkara))
    (failure "Oh nous, not a Kebab!")
    (let [item (assoc data :id (swap! ids inc))]
      (swap! db assoc (:id item) item)
      (success item))))

(defnk ^:command reset-kebabs
  "Deletes all kebabs"
  {:roles #{:admin}}
  [[:system db]]
  (reset! db nil)
  (success))

;;
;; Application
;;

(def app
  (cqrs-api
    {:core {:handlers {:kebab [#'get-kebabs #'add-kebab #'reset-kebabs]
                       :math 'example.math
                       :tx [#'transact #'speculative]}
            :context {:system {:db (atom {})
                               :ids (atom 0)
                               :counter (atom 0)}}
            :user {:roles security/require-roles}}
     :ring {:interceptors [security/api-key-authenticator]}}))

(comment
  (server/run-server #'app {:port 7000}))

(comment

  {:action :kebab/add-kebab
   :data {:name "Abu Fuad", :type :doner}}

  {:commands
   [{:action :kebab/add-kebab
     :data {:name "Abu Fuad", :type :doner}}
    {:action :kebab/add-kebab
     :data {:name "Kuningaskebab", :type :mustamakkara}}]})
