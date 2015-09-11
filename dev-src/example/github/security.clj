(ns example.github.security
  (:require [kekkonen.cqrs :refer :all]
            [plumbing.core :as p]
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
               "123" {:name "Seppo" :roles #{}}
               "234" {:name "Sirpa" :roles #{:boss}}
               nil)]
    (assoc context :user user)))

(defn require-roles [context required]
  (let [roles (-> context :user :roles)]
    (if (seq (set/intersection roles required))
      context
      (failure! {:code "Missing role"
               :roles roles
               :required required}))))

(p/defnk ^:query get-user
  {:responses {success-status {:schema (s/maybe User)}}}
  [user] (success user))
