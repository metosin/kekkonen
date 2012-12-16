(ns meve.core
  (:use [meve.log]))

(defonce commands (atom {}))

(defn to-request-validator [request-validators args]
  ; TODO
  (fn [request]))

(defn error? [v]
  (and (vector? v) (= (first v) :error)))

(defn to-before-fn [pre-fns]
  (fn [request]
    (reduce
      (fn [req pre-fn]
        (if (error? req)
          req
          (pre-fn req)))
      request
      pre-fns)))

(defn to-after-fn [post-fns]
  (fn [request response]
    (reduce
      (fn [resp post-fn]
        (if (or (error? resp) (= :retry resp))
          resp
          (post-fn request resp)))
      response
      post-fns)))

(defmacro defcommand [command-name properties args & statements]
  (let [validate (to-request-validator (:validators properties) args)
        before (to-before-fn (:before properties))
        after (to-after-fn (:after properties))]
    `(do
       (defn ~command-name [req#]
         (let [errors# (~validate req#)]
           (if-not (empty? errors#)
             [:error {:status 412 :body (str errors#)}]
             (loop [retries# 0]
               (let [request# (~before req#)
                     response# (let [~'request request#
                                     ~'request-body (:body request#)
                                     ~@(interleave args (map #(list `get 'request-body (keyword %)) args))]
                                 ~@statements)
                     response# (~after request# response#)]
                 (if-not (= response# :retry)
                   response#
                   (if (> retries# 10)
                     [:error {:status 500 :body "Too many retries"}]
                     (recur (inc retries#)))))))))
       (swap! commands assoc ~(str command-name) ~command-name))))

