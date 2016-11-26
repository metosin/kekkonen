(ns example.io
  (:require [kekkonen.core :as k]
            [plumbing.core :refer [defnk]]))

(defrecord IO [io])

(defnk ^:handler save-user [data db]
  (->IO
    (concat
      [[:io/db data]]
      (if-not (@db data)
        [[:io/email data]]))))

(defnk ^:io db [data db]
  (swap! db conj data)
  (println ".. saved to db:" data))

(defnk ^:io email [data]
  (println ".. sent email:" data))

(def io-interceptor
  {:leave (fn [{:keys [response ::k/dispatcher] :as ctx}]
            (when (instance? IO response)
              (doseq [[io data] (:io response)]
                (k/invoke dispatcher io {:data data})))
            ctx)})

(def k
  (k/dispatcher
    {:handlers {:action [#'save-user]
                :io [#'db #'email]}
     :context {:db (atom #{})}
     :type-resolver (k/type-resolver :handler :io)
     :interceptors [io-interceptor]}))

(println (k/invoke k :action/save-user {:data "Laura"}))
; .. saved to db: Laura
; .. sent email: Laura
; #example.io.IO{:io ([:io/db Laura] [:io/email Laura])}

(println (k/invoke k :action/save-user {:data "Lotta"}))
; .. saved to db: Lotta
; .. sent email: Lotta
; #example.io.IO{:io ([:io/db Lotta] [:io/email Lotta])}

(println (k/invoke k :action/save-user {:data "Lotta"}))
; .. saved to db: Lotta
; #example.io.IO{:io ([:io/db Lotta])}
