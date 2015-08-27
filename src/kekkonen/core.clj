(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.fnk.pfnk :as pfnk])
  (:import [clojure.lang Var]))

;;
;; collecting actions
;;

(s/defn user-meta [v :- Var]
  (-> v meta (dissoc :schema :ns :name :file :column :line :doc)))

(s/defschema ActionMeta
  "Action metadata"
  {:fn s/Any
   :name s/Keyword
   :user {s/Keyword s/Any}
   :description (s/maybe s/Str)
   :input s/Any
   :output s/Any
   :source-map {:line s/Int
                :column s/Int
                :file s/Str
                :ns s/Symbol
                :name s/Symbol}
   s/Keyword s/Any})

(s/defn defnk->action :- (s/maybe ActionMeta)
  "Converts a defnk into an Action. Returns nil if the given
  var does not contain the defnk :schema metadata"
  [v :- Var]
  (let [{:keys [line column file ns name doc schema]} (meta v)]
    (if schema
      {:fn @v
       :name (keyword name)
       :user (user-meta v)
       :description doc
       :input (pfnk/input-schema @v)
       :output (pfnk/output-schema @v)
       :source-map {:line line
                    :column column
                    :file file
                    :ns (ns-name ns)
                    :name name}})))

(s/defn collect-ns :- [ActionMeta]
  "Collects all public vars from a given namespace, which
  can be transformed by the transformer fn (given a Var)."
  ([ns] (collect-ns ns defnk->action))
  ([ns transformer]
   (some->> ns
            ns-publics
            (keep (comp transformer val))
            vec)))
