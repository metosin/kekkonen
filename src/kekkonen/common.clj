(ns kekkonen.common
  (:require [clojure.walk :as walk]
            [schema.core :as s]))

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

(defn copy-from-to [data [from to]]
  (assoc-in data to (get-in data from)))

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

(defn any-map-schema? [schema]
  (or (= schema s/Any)
      (= schema {s/Keyword s/Any})))
