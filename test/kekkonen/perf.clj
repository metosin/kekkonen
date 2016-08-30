(ns kekkonen.perf
  (:require [criterium.core :as cc]
            [kekkonen.core :as k]
            [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]))

(defn title [s]
  (println (str "\u001B[35m" (apply str (repeat (count s) "#")) "\u001B[0m"))
  (println (str "\u001B[35m" s "\u001B[0m"))
  (println (str "\u001B[35m" (apply str (repeat (count s) "#")) "\u001B[0m")))

;;
;; handlers & dispatcher
;;

(p/defnk ^:handler plus1 :- {:body {:result s/Int}, s/Keyword s/Any}
  "adds numbers together"
  [[:data x :- s/Int, y :- s/Int]]
  (success {:result (+ x y)}))

(def d1 (k/dispatcher {:handlers {:api {:math #'plus1}}}))
(def d2 (k/dispatcher {:handlers {:api {:math #'plus1}}
                       :coercion {:input nil, :output nil}}))

;;
;; clojure multimethod
;;

(defmulti multi-method-invoke (fn [key _] key))
(defmethod multi-method-invoke :api.math/plus1 [_ data] (plus1 data))

;;
;; benchmarks
;;

(defn core-bench []

  (title "with coercion")
  (cc/quick-bench (k/invoke d1 :api.math/plus1 {:data {:x 10, :y 20}}))
  ; 28.0µs
  ;  8.2µs (memoized)
  ;  7.0µs (lookup)
  ;  7.2µs (leave)
  ; 10.0µs (pedestal)
  ;  9.6µs (precompiled)
  ;  9.0µs (records)

  (title "without coercion")
  (cc/quick-bench (k/invoke d2 :api.math/plus1 {:data {:x 10, :y 20}}))
  ; 3.7µs
  ; 3.7µs (memoized)
  ; 2.0µs (lookup)
  ; 2.1µs (leave)
  ; 4.2µs (pedestal)
  ; 3.9µs (precompiled)
  ; 3.7µs (records)

  (title "clojure multimethod")
  (cc/quick-bench (multi-method-invoke :api.math/plus1 {:data {:x 10, :y 20}}))
  ; 0.3µs

  (println))

;;
;; ring-handlers
;;

(require '[kekkonen.ring :as kr])

(def r1 (kr/ring-handler d1))
(def r2 (kr/ring-handler d2))
(def r3 (kr/ring-handler d2 {:coercion nil}))

(def data {:uri "/api/math/plus1"
           :request-method :post
           :body-params {:x 10, :y 20}})

(defn ring-bench []

  (title "ring & dispatcher coercion")
  (assert (= 30 (-> data r1 :body :result)))
  (cc/quick-bench (r1 data))
  ; 20.7µs
  ; 17.1µs
  ; 11.3µs
  ; 14.7µs (leave)
  ; 19.2µs (pedestal)
  ; 19.0µs (precompiled)
  ; 18.3µs (records)
  ; 16.4µs (cleanup)

  (title "ring coercion")
  (assert (= 30 (-> data r2 :body :result)))
  (cc/quick-bench (r2 data))
  ; 15.7µs
  ; 12.2µs
  ;  7.6µs
  ; 10.4µs (leave)
  ; 13.3µs (pedestal)
  ; 13.1µs (precompiled)
  ; 12.9µs (records)
  ; 10.1µs (cleanup)

  (title "no coercion")
  (assert (= 30 (-> data r3 :body :result)))
  (cc/quick-bench (r3 data))
  ; 3.5µs
  ; 3.9µs (leave)
  ; 9.5µs (pedestal)
  ; 9.1µs (precompiled)
  ; 8.6µs (records)
  ; 6.2µs (cleanup)

  (println))

(comment
  (core-bench)
  (ring-bench))
