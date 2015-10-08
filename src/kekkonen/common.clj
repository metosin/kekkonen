(ns kekkonen.common
  (:require [clojure.walk :as walk]
            [schema.core :as s]
            [lazymap.core :as lm])
  (:import [clojure.lang IDeref]))

(defn- deep-merge* [& maps]
  (let [f (fn [old new]
            (if (and (map? old) (map? new))
              (merge-with deep-merge* old new)
              new))]
    (if (every? map? maps)
      (apply merge-with f maps)
      (last maps))))

(defn deep-merge [& maps]
  (let [maps (filter identity maps)]
    (assert (every? map? maps))
    (apply merge-with deep-merge* maps)))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. `keys` is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn strip-nil-values
  "Recursively strip away nils and empty maps"
  [m]
  (walk/postwalk
    (fn [x]
      (if (and (not (record? x)) (map? x))
        (into (empty x) (remove (comp #(or (nil? %) (and (map? %) (empty? %))) val) x))
        x))
    m))

(defn deep-merge-from-to [data [from to]]
  (update-in data to deep-merge (get-in data from)))

(defn deep-merge-to-from [data [to from]]
  (deep-merge-from-to data [from to]))

(defn move-from-to [data [from to]]
  (if-let [target (get-in data from)]
    (-> data
        (assoc-in to target)
        (dissoc-in from))
    data))

(defn move-to-from [data [to from]]
  (move-from-to data [from to]))

(defn merge-map-schemas [& schemas]
  (reduce
    (fn [acc schema]
      (if-not (= schema s/Any)
        (merge acc schema)
        acc))
    {} schemas))

;;
;; lazy
;;

(defn lazy-map
  ([]
   (lazy-map {}))
  ([m]
   (if (satisfies? lm/ILazyPersistentMap m) m (merge (lm/create-lazy-map {}) m))))

(defn lazy-assoc-in
  "Value should be either derefable (delay or future) which will be dereffed when value is needed
  or a function which will be called."
  [m [k & ks] f]
  (if (seq ks)
    (assoc m k (lazy-assoc-in (or (get m k) (lazy-map)) ks f))
    (let [m (if (satisfies? lm/ILazyPersistentMap m) m (lazy-map m))]
      (lm/delayed-assoc m k (cond
                              (instance? IDeref f) f
                              (fn? f) (delay (f))
                              :else (throw
                                      (ex-info "lazy-assoc-in requres a derefable or a fn value" {:value f})))))))
