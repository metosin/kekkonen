(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [clojure.string :as str]
            [plumbing.fnk.pfnk :as pfnk])
  (:import [clojure.lang Var Keyword]))

(s/defn user-meta [v :- Var]
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

(p/defnk collect :- [{s/Keyword [Handler]}]
  "Collects actions from modules into a map of module->[Action]"
  [modules :- {s/Keyword s/Any}
   {collector :- s/Any defnk->handler}]
  (p/map-vals (partial collect-ns collector) modules))

(s/defn create
  "Creates a routing table of modules and actions."
  [module->actions :- {s/Keyword [Handler]}]
  (p/for-map [[module actions] module->actions]
    module (p/for-map [action actions]
             (:name action) (assoc action :module module))))

(s/defn action-path [path :- s/Keyword]
  (-> path str (subs 1) (str/split #"/") (->> (mapv keyword))))

(s/defn some-action :- (s/maybe Handler)
  [kekkonen, path :- s/Keyword]
  (get-in kekkonen (action-path path)))

(s/defn invoke [kekkonen path data]
  (if-let [action (some-action kekkonen path)]
    ((:fn action) data)
    #_ "throw an exception"))

