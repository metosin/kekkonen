(ns example.io2
  (:require [kekkonen.core :as k]
            [plumbing.core :refer [defnk]]))

;;
;; IO
;;

(defrecord IO [io])

(defrecord IOResult [io acc])

(defn run-io! [dispatcher {:keys [io]}]
  (map->IOResult
    (reduce
      (fn [{:keys [io acc]} [action data]]
        (let [io-result (k/invoke dispatcher action {:data data, :acc acc})]
          (when-not (or (map? io-result) (nil? io-result))
            (throw (ex-info "IO must return a map or nil" {:result io-result})))
          {:io (conj io [action data io-result])
           :acc (merge acc io-result)}))
      {:io []
       :acc {}}
      io)))

(defn log! [fmt & args]
  (apply printf (str "\u001B[35m" fmt "\u001B[0m\n") args))

(def io-interceptor
  {:leave (fn [{:keys [response ::k/dispatcher] :as ctx}]
            (if (instance? IO response)
              (update ctx :response (partial run-io! dispatcher))
              ctx))})

;;
;; Application
;;

;; actions

(defnk ^:handler save-user [data db]
  (->IO
    (concat
      [[:io/db data]]
      (if-not (@db data)
        [[:io/email data]]))))

;; side-effects

(defnk ^:io db [data ids db]
  (let [id (swap! ids inc)]
    (swap! db assoc data id)
    (log! ".. created user %s (id=%s)" data id)
    {:id id}))

(defnk ^:io email [data [:acc id]]
  (log! ".. sent email to %s (id=%s)" data id))

;; dispatcher

(def dispatcher
  (k/dispatcher
    {:handlers {:action [#'save-user]
                :io [#'db #'email]}
     :context {:db (atom {}), :ids (atom 0)}
     :type-resolver (k/type-resolver :handler :io)
     :interceptors [io-interceptor]}))

;;
;; Running it
;;

(println (k/invoke dispatcher :action/save-user {:data "Laura"}))
; .. created user Laura (id=1)
; .. sent email to Laura (id=1)
; #example.io2.IOResult{:io [[:io/db Laura {:id 1}] [:io/email Laura nil]], :acc {:id 1}}

(println (k/invoke dispatcher :action/save-user {:data "Lotta"}))
; .. created user Lotta (id=2)
; .. sent email to Lotta (id=2)
; #example.io2.IOResult{:io [[:io/db Lotta {:id 2}] [:io/email Lotta nil]], :acc {:id 2}}

(println (k/invoke dispatcher :action/save-user {:data "Lotta"}))
; .. created user Lotta (id=3)
; #example.io2.IOResult{:io [[:io/db Lotta {:id 3}]], :acc {:id 3}}
