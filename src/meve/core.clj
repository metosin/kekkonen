(ns meve.core
  (:use [meve.log]))

(defonce commands (atom {}))

(defn resolve-param [request arg]
  (or
    (get-in request [:body arg])
    (get-in request [:body (keyword arg)])
    (get request arg)
    (get request (keyword arg))))

(defmacro defcommand [command-name middleware args & statements]
  `(do
     (def ~command-name
       (reduce
         (fn [current# middleware#]
           (middleware# current#))
         (fn [~'request]
           (let [~@(interleave args (map #(list `resolve-param 'request (str %)) args))]
             ~@statements))
         (reverse ~middleware)))
     (swap! commands assoc ~(str command-name) ~command-name)))
