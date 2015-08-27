(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [clojure.string :as str]
            [plumbing.fnk.pfnk :as pfnk])
  (:import [clojure.lang Var Keyword]))

(s/defn ^:private user-meta [v :- Var]
  (-> v meta (dissoc :schema :ns :name :file :column :line :doc)))

(s/defschema Handler
  "Action handler metadata"
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

(s/defn defnk->handler :- (s/maybe Handler)
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

(s/defn collect-ns :- [Handler]
  "Collects all public vars from a given namespace, which
  can be transformed by the transformer fn (given a Var)."
  [collector ns]
  (some->> ns
           ns-publics
           (keep (comp collector val))
           vec))

(s/defn collect :- {s/Keyword [Handler]}
  "Collects actions from modules into a map of module->[Action]"
  ([modules] (collect modules defnk->handler))
  ([modules :- {s/Keyword s/Symbol}
    collector :- s/Any]
    (p/map-vals (partial collect-ns collector) modules)))

(p/defnk create
  "Creates a routing table of modules and actions."
  [modules :- {s/Keyword [Handler]}
   {inject :- {s/Keyword s/Any} {}}]
  (merge
    {:inject inject}
    (p/for-map [[module actions] modules]
      module (p/for-map [action actions]
               (:name action) (assoc action :module module)))))

(s/defn ^:private action-kws [path :- s/Keyword]
  (-> path str (subs 1) (str/split #"/") (->> (mapv keyword))))

(s/defn some-handler :- (s/maybe Handler)
  "Returns a handler or nil"
  [kekkonen, action :- s/Keyword]
  (get-in kekkonen (action-kws action)))

(s/defn invoke
  "Invokes a action handler with the given context."
  ([kekkonen action]
    (invoke kekkonen action {}))
  ([kekkonen action request]
    (let [handler (some-handler kekkonen action)
          ; context-level overrides of inject!
          context (merge (:inject kekkonen) request)]
      (if-not handler
        (throw (ex-info (str "invalid action " action) {}))
        ((:fn handler) context)))))

