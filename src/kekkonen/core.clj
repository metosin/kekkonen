(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [clojure.string :as str]
            [clojure.walk :as w]
            [plumbing.fnk.pfnk :as pfnk]
            [kekkonen.common :as kc]
            [clojure.walk :as walk])
  (:import [clojure.lang Var IPersistentMap Symbol PersistentVector AFunction Keyword])
  (:refer-clojure :exclude [namespace]))

;;
;; Definitions
;;

(s/defschema Function
  (s/=> {s/Keyword s/Any} s/Any))

(s/defschema KeywordMap
  {s/Keyword s/Any})

(s/defschema Context
  (merge
    KeywordMap
    {(s/optional-key :data) s/Any}))

(s/defschema Handler
  {:function Function
   :type s/Keyword
   :name s/Keyword
   :ns (s/maybe s/Keyword)
   :action s/Keyword
   :user KeywordMap
   (s/optional-key :ns-user) [KeywordMap]
   (s/optional-key :all-user) [KeywordMap]
   :description (s/maybe s/Str)
   :input s/Any
   :output s/Any
   (s/optional-key :source-map) {:line s/Int
                                 :column s/Int
                                 :file s/Str
                                 :ns s/Symbol
                                 :name s/Symbol}
   s/Keyword s/Any})

(s/defschema Registry
  {:handlers KeywordMap
   :context KeywordMap
   :transformers [Function]
   :user KeywordMap
   s/Keyword s/Any})

;;
;; Type Resolution
;;

(s/defn type-resolver [& types :- [s/Keyword]]
  (fn [meta]
    (reduce
      (fn [_ type]
        (if (or (some-> meta type true?) (some-> meta :type (= type)))
          (reduced (-> meta (assoc :type type) (dissoc type)))))
      nil types)))

(s/defn any-type-resolver [meta]
  (if (:type meta) meta))

(def default-type-resolver (type-resolver :handler))

;;
;; Collecting
;;

(defprotocol Collector
  (-collect [this type-resolver]))

(s/defn collect
  [collector type-resolver]
  (-collect collector type-resolver))

;;
;; Handlers
;;

(s/defn ^:private user-meta [meta :- KeywordMap]
  (dissoc
    meta
    ; reserved registry handler stuff
    :type :input :output :description
    ; clojure var meta
    :line :column :file :name :ns :doc
    ; plumbing details
    :schema :plumbing.fnk.impl/positional-info))

(s/defn handler
  [meta :- KeywordMap, f :- Function]
  (assert (:name meta) "handler should have :name")
  (vary-meta f merge {:type :handler} meta))

(defn handler? [x]
  (and (map? x) (:function x) (:type x)))

;;
;; Namespaces
;;

(s/defrecord Namespace [name :- s/Keyword, meta :- KeywordMap]
  Collector
  (-collect [this _]
    this))

(s/defn namespace [meta :- KeywordMap]
  (->Namespace (:name meta) (dissoc meta :name)))

;;
;; Collection helpers
;;

(extend-type AFunction
  Collector
  (-collect [this type-resolver]
    (if-let [{:keys [name description schema type input output] :as meta} (type-resolver (meta this))]
      (if name
        {(namespace
           {:name (keyword name)})
         {:function this
          :type type
          :name (keyword name)
          :user (user-meta meta)
          :description (or description "")
          :input (or (and schema (pfnk/input-schema this)) input s/Any)
          :output (or (and schema (pfnk/output-schema this)) output s/Any)}})
      (throw (ex-info (format "Function %s can't be type-resolved" this) {:target this})))))

(extend-type Var
  Collector
  (-collect [this type-resolver]
    (if-let [{:keys [line column file ns name doc schema type] :as meta} (type-resolver (meta this))]
      (if (and name schema)
        {(namespace
           {:name (keyword name)})
         {:function @this
          :type type
          :name (keyword name)
          :user (user-meta meta)
          :description doc
          :input (pfnk/input-schema @this)
          :output (pfnk/output-schema @this)
          :source-map {:line line
                       :column column
                       :file file
                       :ns (ns-name ns)
                       :name name}}})
      (throw (ex-info (format "Var %s can't be type-resolved" this) {:target this})))))

(extend-type Symbol
  Collector
  (-collect [this type-resolver]
    (require this)
    (some->> this
             ns-publics
             (map val)
             (filter #(type-resolver (meta %)))
             (map #(-collect % type-resolver))
             (apply merge))))

(extend-type Keyword
  Collector
  (-collect [this _]
    (namespace {:name this})))

(extend-type PersistentVector
  Collector
  (-collect [this type-resolver]
    (->> this
         (map #(-collect % type-resolver))
         (apply merge))))

(extend-type IPersistentMap
  Collector
  (-collect [this type-resolver]
    (p/for-map [[k v] this]
      (-collect k type-resolver) (-collect v type-resolver))))

;;
;; Registry
;;

(s/defschema Options
  {:handlers KeywordMap
   (s/optional-key :context) KeywordMap
   (s/optional-key :type-resolver) Function
   (s/optional-key :transformers) [Function]
   (s/optional-key :user) KeywordMap
   s/Keyword s/Any})

(s/def +default-options+ :- Options
  {:handlers {}
   :context {}
   :transformers []
   :type-resolver default-type-resolver
   :user {}})

;; TODO: just collect handlers into a list?
(defn- collect-and-enrich
  [handlers type-resolver allow-empty-namespaces?]
  (let [handler-ns (fn [m] (if (seq m) (->> m (map :name) (map name) (str/join ".") keyword)))
        collect-ns-meta (fn [m] (if (seq m) (->> m (map :meta) (filterv (complement empty?)))))
        handler-action (fn [n ns] (keyword (str/join "/" (map name (filter identity [ns n])))))
        enrich (fn [h m]
                 (if (or (seq m) allow-empty-namespaces?)
                   (let [ns (handler-ns m)
                         ns-user (collect-ns-meta m)
                         user-meta (:user h)
                         all-user (if-not (empty? user-meta) (conj ns-user user-meta) ns-user)]
                     (merge h {:ns ns
                               :ns-user ns-user
                               :all-user all-user
                               :action (handler-action (:name h) ns)}))
                   (throw (ex-info "can't define handlers into empty namespace" {:handler h}))))
        traverse (fn traverse [x m]
                   (p/for-map [[k v] x]
                     (:name k) (if (handler? v)
                                 (enrich v m)
                                 (traverse v (conj m k)))))]
    (traverse (collect handlers type-resolver) [])))

(s/defn create :- Registry
  "Creates a Registry."
  [options :- Options]
  (let [options (kc/deep-merge +default-options+ options)
        handlers (collect-and-enrich (:handlers options) (:type-resolver options) false)]
    {:context (:context options)
     :handlers handlers
     :transformers (:transformers options)
     :user (:user options)}))

(s/defn action-kws [action :- s/Keyword]
  (let [tokens (str/split (subs (str action) 1) #"/")
        nss (vec (apply concat (map #(str/split % #"\.") (butlast tokens))))
        name (last tokens)]
    (map keyword (conj nss name))))

(s/defn some-handler :- (s/maybe Handler)
  "Returns a handler or nil"
  [registry :- Registry, action :- s/Keyword]
  (get-in (:handlers registry) (action-kws action)))

(s/defn transform-handlers
  "Applies f to all handlers. If the call returns nil,
  the handler is removed."
  [registry :- Registry, f :- Function]
  (merge
    registry
    {:handlers
     (kc/strip-nil-values
       (w/prewalk
         (fn [x]
           (if (handler? x)
             (f x)
             x))
         (:handlers registry)))}))

; TODO: we should give separate root-handlers?
(s/defn inject
  "Injects handlers into an existing Registry"
  [registry :- Registry, handler]
  (let [handler (collect-and-enrich handler any-type-resolver true)]
    (update-in registry (into [:handlers] (:ns handler)) merge handler)))

;;
;; Calling handlers
;;

(s/defn ^:private prepare
  "Prepares a context for invocation or validation. Returns an 0-arity function
  or throws exception."
  [registry :- Registry, action :- s/Keyword, context :- Context]
  (if-let [{:keys [function all-user] :as handler} (some-handler registry action)]
    (let [context (as-> context context

                        ;; base-context from Registry
                        (kc/deep-merge (:context registry) context)

                        ;; run all the transformers
                        (reduce
                          (fn [context mapper]
                            (mapper context))
                          context
                          (:transformers registry))

                        ;; run all the user transformers per namespace/handler, start from the root
                        (reduce
                          (fn [context [k v]]
                            (if-let [mapper (get-in registry [:user k])]
                              (mapper context v)
                              context))
                          context
                          (apply concat all-user))

                        ;; inject in stuff the context
                        (merge context {::registry registry
                                        ::handler handler}))]
      (fn [invoke?]
        (if invoke?
          (function context)
          true)))
    (throw (ex-info (str "Invalid action " action) {}))))

(s/defn invoke
  "Invokes an action handler with the given context."
  ([registry :- Registry, action :- s/Keyword]
    (invoke registry action {}))
  ([registry :- Registry, action :- s/Keyword, context :- Context]
    ((prepare registry action context) true)))

(s/defn validate
  "Checks if context is valid for the handler (without calling the body)"
  ([registry :- Registry, action :- s/Keyword]
    (prepare registry action {}))
  ([registry :- Registry, action :- s/Keyword, context :- Context]
    ((prepare registry action context) false)))

;;
;; Listing handlers
;;

(s/defn all-handlers :- [Handler]
  "Returns all handlers."
  [registry :- Registry]
  (let [handlers (atom [])]
    (transform-handlers
      registry
      (fn [handler]
        (swap! handlers conj handler) nil))
    @handlers))

(s/defn available-handlers :- [Handler]
  "Returns all handlers which are available under a given context"
  [context :- Context, registry :- Registry]
  (filter
    (fn [handler]
      (try
        (validate registry (:action handler) context)
        (catch Exception _)))
    (all-handlers registry)))

(defn stringify-schema [schema]
  (walk/prewalk
    (fn [x]
      (if-not (or (and (map? x) (not (record? x))) (vector? x) (string? x) (keyword? x))
        (pr-str x) x))
    schema))

; TODO: pass Schemas as-is -> implement https://github.com/metosin/web-schemas
(s/defn public-meta
  [handler :- Handler]
  (-> handler
      (select-keys [:input :name :ns :output :source-map :type :action])
      (update :input stringify-schema)
      (update :output stringify-schema)))

;;
;; Working with contexts
;;

(s/defn get-registry [context :- Context]
  (get context ::registry))

(s/defn get-handler [context :- Context]
  (get context ::handler))

(s/defn with-context [registry :- Registry, context :- Context]
  (update-in registry [:context] kc/deep-merge context))

(s/defn context-copy
  "Returns a function that assocs in a value from to-kws path into from-kws in a context"
  [from :- [s/Any], to :- [s/Any]]
  (s/fn [context :- Context]
    (assoc-in context to (get-in context from {}))))

(s/defn context-dissoc [from-kws :- [s/Any]]
  "Returns a function that dissocs in a value from from-kws in a context"
  (s/fn [context :- Context]
    (kc/dissoc-in context from-kws)))
