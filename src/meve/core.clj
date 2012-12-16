(ns meve.core
  (:use [meve.log]))

(defonce commands (atom {}))

(defmacro make-handler [args statements]
  `(fn [request#]
     (let [~'request-body (:body request#)
           ~@(interleave args (map #(list `get 'request-body (keyword %)) args))]
       ~@statements)))

(defmacro defcommand [command-name middleware args & statements]
  `(do
     (def ~command-name (reduce (fn [current mw] (mw current)) (make-handler ~args ~statements) (reverse ~middleware)))
     (swap! commands assoc ~(str command-name) ~command-name)))
