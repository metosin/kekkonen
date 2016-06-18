(ns kekkonen.common
  (:require [clojure.walk :as walk]
            [schema.core :as s]
            [plumbing.core :as p]
            [linked.core :as linked]
            [plumbing.fnk.pfnk :as pfnk]))

(defn map-like?
  "Checks wether x is a map or vector of tuples"
  [x]
  (boolean
    (or (map? x)
        (and (vector? x)
             (seq x)
             (every? vector? x)
             (every? #(= (count %) 2) x)))))

(defn merge-map-like
  "Merges map-like collections into a linked map"
  [& cols]
  (into (linked/map) (apply concat cols)))

;;
;; Deep Merge: fast
;;

(defn- deep-merge* [& colls]
  (let [f (fn [old new]
            (if (and (map? old) (map? new))
              (merge-with deep-merge* old new)
              new))]
    (if (every? map? colls)
      (apply merge-with f colls)
      (last colls))))

(defn deep-merge
  "Deep-merges things together"
  [& coll]
  (let [values (filter identity coll)]
    (if (every? map? values)
      (apply merge-with deep-merge* values)
      (last values))))

;;
;; Deep Merge: slower, merges all map-like forms
;;

(defn- deep-merge-map-like* [& maps]
  (let [f (fn [old new]
            (if (and (map-like? old) (map-like? new))
              (merge-with deep-merge-map-like* (merge-map-like old) (merge-map-like new))
              new))]
    (if (every? map-like? maps)
      (apply merge-with f (map merge-map-like maps))
      (last maps))))

(defn deep-merge-map-like
  "Deep-merges maps together, non-map-likes are overridden"
  [& maps]
  (let [maps (filter identity maps)]
    (assert (every? map? maps))
    (apply merge-with deep-merge-map-like* maps)))

;;
;; Others
;;

(defn join [& x-or-xs]
  (flatten x-or-xs))

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

(defn copy-to-from [data [to from]]
  (copy-from-to data [from to]))

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
        (deep-merge acc schema)
        acc))
    {} schemas))

(defn any-map-schema? [schema]
  (or (= schema s/Any)
      (= schema {s/Keyword s/Any})))

;;
;; Schema tools
;;

(defn extract-schema
  ([x]
   (extract-schema x s/Any))
  ([x default]
   (p/for-map [k [:input :output]
               :let [schema (let [pfnk-schema (case k
                                                :input pfnk/input-schema
                                                :output pfnk/output-schema)
                                  pfnk? (fn [x] (and (satisfies? pfnk/PFnk x) (:schema (meta x))))]
                              (if (var? x)
                                (cond
                                  (pfnk? @x) (pfnk-schema @x)
                                  :else (or (-> x meta k) s/Any))
                                (or (and (-> x meta :schema) (pfnk-schema x))
                                    ;; TODO: maek it better
                                    (and (= :input k) (:input x))
                                    (-> x meta k)
                                    default)))]
               :when schema]
     k schema)))
