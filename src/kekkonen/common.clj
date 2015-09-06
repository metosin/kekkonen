(ns kekkonen.common)

(defn deep-merge
  "Recursively merges maps.
   If the first parameter is a keyword it tells the strategy to
   use when merging non-map collections. Options are
   - :replace, the default, the last value is used
   - :into, if the value in every map is a collection they are concatenated
     using into. Thus the type of (first) value is maintained."
  {:arglists '([strategy & values] [values])}
  [& values]
  (let [[values strategy] (if (keyword? (first values))
                            [(rest values) (first values)]
                            [values :replace])]
    (cond
      (every? map? values)
      (apply merge-with (partial deep-merge strategy) values)

      (and (= strategy :into) (every? coll? values))
      (reduce into values)

      :else
      (last values))))

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
  "removes map-keys with nil values"
  [m] (into {} (filter (comp not nil? second) m)))
