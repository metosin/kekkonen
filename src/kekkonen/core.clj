(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.fnk.pfnk :as pfnk])
  (:import [clojure.lang Var]))

;;
;; action types
;;

(s/defn command? [v :- Var]
  (-> v meta :command true?))

(s/defn query? [v :- Var]
  (-> v meta :query true?))

(defn command-or-query? [v]
  ((some-fn command? query?) v))

(defn get-type [v]
  (cond
    (command? v) :command
    (query? v) :query
    :else nil))

;;
;; collecting actions
;;

(s/defschema ActionMeta
  "Action metadata"
  {:fn s/Any
   :name s/Keyword
   :type (s/enum :command :query)
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
       :type (get-type v)
       :user {}
       :description doc
       :input (pfnk/input-schema @v)
       :output (pfnk/output-schema @v)
       :source-map {:line line
                    :column column
                    :file file
                    :ns (ns-name ns)
                    :name name}})))

(defn collect-ns
  "Collects all public vars from a given namespace, which "
  ([ns] (collect-ns ns defnk->action))
  ([ns keeper]
   (some->> ns
            ns-publics
            (keep (comp keeper val))
            vec)))
