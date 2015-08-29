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
   (s/optional-key :source-map) {:line s/Int
                :column s/Int
                :file s/Str
                :ns s/Symbol
                :name s/Symbol}
   s/Keyword s/Any})

(s/defn defnk->handler :- (s/maybe Handler)
  "Converts a defnk into an Handler. Returns nil if the given
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
  can be transformed by defnk->handler fn (given a Var)."
  [ns]
  ; is this a good idea?
  (require ns)
  (some->> ns
           ns-publics
           (keep (comp defnk->handler val))
           vec))

(s/defn collect-ns-map :- {s/Keyword [Handler]}
  "Collects handlers from modules into a map of module->[Action]"
  [modules :- {s/Keyword s/Symbol}]
  (p/map-vals collect-ns modules))

(s/defschema Modules
  "Modules form a tree."
  {s/Keyword (s/either [Handler] (s/recursive #'Modules))})

(p/defnk create
  "Creates a Kekkonen."
  [modules :- Modules
   {inject :- {s/Keyword s/Any} {}}]
  (let [traverse (fn f [x m]
                   (p/for-map [[k v] x]
                     k (if (vector? v)
                         (p/for-map [h v] (:name h) (assoc h :module (conj m k)))
                         (f v (conj m k)))))]
    {:inject inject
     :modules (traverse modules [])}))

(s/defn ^:private action-kws [path :- s/Keyword]
  (-> path str (subs 1) (str/split #"/") (->> (mapv keyword))))

(s/defn some-handler :- (s/maybe Handler)
  "Returns a handler or nil"
  [kekkonen, action :- s/Keyword]
  (get-in (:modules kekkonen) (action-kws action)))

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
