(ns example.github.api
  (:require [example.github.security :as security]
            [ring.adapter.jetty :as jetty]
            [kekkonen.cqrs :refer :all]
            [plumbing.core :as p]
            [schema.core :as s]))

;;
;; Schemas
;;

(s/defschema Repository
  "A Repository"
  {:id s/Int
   :name s/Str
   :allowed #{s/Str}
   :watchers #{s/Str}
   :stargazers #{s/Str}})

;;
;; Commands & Queries
;;

(p/defnk ^:query list-repositorys
  "Retrieves all Repos"
  {:responses {success-status {:schema [Repository]}}}
  [[:components repos]]
  (success (vals @repos)))

(p/defnk ^:query get-repository
  "Retrieves repo details"
  {:responses {success-status {:schema (s/maybe Repository)}}}
  [[:components repos]
   [:data id :- s/Str]]
  (success (@repos id)))

(p/defnk ^:command fork
  "Forks a repo"
  [[:data id :- s/Str]]
  (success {:forked id}))

(p/defnk ^:query ping [] (success {:ping "pong"}))
(p/defnk ^:query pong [] (success {:pong "ping"}))


(do
  ;;
  ;; calculators
  ;;

  (p/defnk ^:query plus
    {:responses {success-status {:schema {:result s/Int}}}}
    [[:data x :- s/Int, y :- s/Int]]
    (success {:result (+ x y)}))

  (p/defnk ^:query times
    {:responses {success-status {:schema {:result s/Int}}}}
    [[:data x :- s/Int, y :- s/Int]]
    (success {:result (* x y)}))

  (p/defnk ^:command inc!
    {:responses {success-status {:schema {:result s/Int}}}}
    [[:components counter]]
    (success {:result (swap! counter inc)})))

;;
;; Application
;;

(def app
  (cqrs-api
    {:info {:info {:title "Kekkonen"}}
     :core {:handlers {:api {:github [#'get-repository #'list-repositorys #'fork]
                             :calculator [#'plus #'times #'inc!]
                             :security #'security/get-user
                             :system {:ping [#'ping #'pong]}}}
            :context {:components {:repos (atom {})
                                   :counter (atom 0)}}
            :user {:roles security/require-roles}}
     :ring {:transformers [security/api-key-authenticator]}}))

;;
;; Main
;;

(defn start []
  (jetty/run-jetty
    #'app
    {:port 3000
     :join? false}))

(comment
  (start))
