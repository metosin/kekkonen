;; ns partially copy-pasted from pedestal. Will be merged later.
(ns kekkonen.interceptor
  (:import [clojure.lang PersistentQueue]
           [java.util.concurrent.atomic AtomicLong]))

(def ^:private ^AtomicLong execution-id (AtomicLong.))

(defn- interceptor-name [interceptor]
  (get interceptor :name (pr-str interceptor)))

(defn- begin [context]
  (if (contains? context ::execution-id)
    context
    (let [execution-id (.incrementAndGet execution-id)]
      (assoc context ::execution-id execution-id))))

(defn- end [context]
  (if (contains? context ::execution-id)
    (dissoc context ::stack ::execution-id)
    context))

(defn- throwable->ex-info [^Throwable t execution-id interceptor stage]
  (ex-info (str "Interceptor Exception: " (.getMessage t))
           (merge {:execution-id execution-id
                   :stage stage
                   :interceptor (interceptor-name interceptor)
                   :exception-type (keyword (pr-str (type t)))
                   :exception t}
                  (ex-data t))
           t))

(defn- try-f [context interceptor stage]
  (if-let [f (get interceptor stage)]
    (try
      (f context)
      (catch Throwable t
        (assoc context ::error (throwable->ex-info t (::execution-id context) interceptor stage))))
    context))

(defn- enter-all [context]
  (loop [context context]
    (let [queue (::queue context)
          stack (::stack context)]
      (if (empty? queue)
        context
        (let [interceptor (peek queue)
              context (-> context
                          (assoc ::queue (pop queue))
                          (assoc ::stack (conj stack interceptor))
                          (try-f interceptor :enter))]
          (cond
            (::error context) (dissoc context ::queue)
            true (recur context)))))))

(defn- try-error [context interceptor]
  (let [execution-id (::execution-id context)]
    (if-let [error-fn (get interceptor :error)]
      (let [ex (::error context)]
        (try
          (error-fn (dissoc context ::error) ex)
          (catch Throwable t
            (if (identical? (type t) (type (:exception ex)))
              context
              (-> context
                  (assoc ::error (throwable->ex-info t execution-id interceptor :error))
                  (update-in [::suppressed] conj ex))))))
      context)))

(defn- leave-all [context]
  (loop [context context]
    (let [stack (::stack context)]
      (if (empty? stack)
        context
        (let [interceptor (peek stack)
              context (assoc context ::stack (pop stack))
              context (if (::error context)
                        (try-error context interceptor)
                        (try-f context interceptor :leave))]
          (recur context))))))

;;
;; Public api
;;

(defn interceptor? [x]
  (if-let [int-vals (vals (select-keys x [:enter :leave :error]))]
    (and (some identity int-vals)
         (every? fn? (remove nil? int-vals))
         (or (interceptor-name x) true)
         true)
    false))

(defn enqueue [context interceptors]
  (update-in context [::queue]
             (fnil into PersistentQueue/EMPTY)
             interceptors))

(defn terminate [context]
  (dissoc context ::queue))

(defn execute [context]
  (let [context (some-> context
                        begin
                        enter-all
                        terminate
                        leave-all
                        end)]
    (if-let [ex (::error context)]
      (throw ex)
      context)))
