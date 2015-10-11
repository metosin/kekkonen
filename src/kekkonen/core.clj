(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [clojure.string :as str]
            [plumbing.fnk.pfnk :as pfnk]
            [plumbing.map :as pm]
            [kekkonen.common :as kc]
            [clojure.walk :as walk]
            [schema.coerce :as sc]
            [schema.utils :as su])
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
   :description (s/maybe s/Str)

   ;; extra meta-data
   :user KeywordMap
   (s/optional-key :ns-user) [KeywordMap]
   (s/optional-key :all-user) [KeywordMap]

   ;; input schemas
   :input s/Any
   :action-input s/Any

   ;; output schemas
   :output s/Any

   (s/optional-key :source-map) {:line s/Int
                                 :column s/Int
                                 :file s/Str
                                 :ns s/Symbol
                                 :name s/Symbol}
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
;; Exposing handler meta-data
;;

(defn- stringify-schema [schema]
  (walk/prewalk
    (fn [x]
      (if-not (or (and (map? x) (not (record? x))) (vector? x) (string? x) (keyword? x))
        (pr-str x) x))
    schema))

; TODO: pass Schemas as-is -> implement https://github.com/metosin/web-schemas
(s/defn public-handler
  [handler :- Handler]
  (some-> handler
          (select-keys [:input :name :ns :output :source-map :type :action])
          (update-in [:input] stringify-schema)
          (update-in [:output] stringify-schema)))

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
    ; reserved dispatcher handler stuff
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

(defn- extract-schema [x]
  (p/for-map [k [:input :output]]
    k (let [pfnk-schema (case k
                          :input pfnk/input-schema
                          :output pfnk/output-schema)
            pfnk? (fn [x] (and (satisfies? pfnk/PFnk x) (:schema (meta x))))]
        (if (var? x)
          (cond
            (pfnk? @x) (pfnk-schema @x)
            :else (or (-> x meta k) s/Any))
          (or (and (-> x meta :schema) (pfnk-schema x))
              (-> x meta k) s/Any)))))

(extend-type AFunction
  Collector
  (-collect [this type-resolver]
    (if-let [{:keys [name description type] :as meta} (type-resolver (meta this))]
      (let [{:keys [input output]} (extract-schema this)]
        (if name
          {(namespace
             {:name (keyword name)})
           {:function this
            :type type
            :name (keyword name)
            :user (user-meta meta)
            :description (or description "")
            :input input
            :output output}}))
      (throw (ex-info (format "Function %s can't be type-resolved" this) {:target this})))))

(extend-type Var
  Collector
  (-collect [this type-resolver]
    (if-let [{:keys [line column file ns name doc type] :as meta} (type-resolver (meta this))]
      (let [{:keys [input output]} (extract-schema this)]
        {(namespace
           {:name (keyword name)})
         {:function @this
          :type type
          :name (keyword name)
          :user (user-meta meta)
          :description doc
          :input input
          :output output
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
;; coercion
;;

(def memoized-coercer (memoize sc/coercer))

(defn coerce! [schema matcher value in type]
  (let [coercer (memoized-coercer schema matcher)
        coerced (coercer value)]
    (if-not (su/error? coerced)
      coerced
      (throw
        (ex-info
          "Coercion error"
          {:type type
           :in in
           :value value
           :schema schema
           :error coerced})))))

;;
;; Dispatcher
;;

(defprotocol IDispatcher
  (get-handlers [this])
  (dispatch [this mode action context]))

(s/defschema Dispatcher (s/protocol IDispatcher))

(s/defn some-handler :- (s/maybe Handler)
  "Returns a handler or nil"
  [dispatcher, action :- s/Keyword]
  ((get-handlers dispatcher) action))

;;
;; Working with contexts
;;

(s/defn get-dispatcher [context :- Context]
  (get context ::dispatcher))

(s/defn get-handler [context :- Context]
  (get context ::handler))

(s/defn with-context [dispatcher :- Dispatcher, context :- Context]
  (update-in dispatcher [:context] kc/deep-merge context))

(defn context-coerce! [context schema]
  (if-let [coercions (some-> context ::coercion (pm/flatten))]
    (reduce
      (fn [ctx [ks coerce-fn]]
        (if-let [coercion-schema (get-in schema ks)]
          (assoc-in ctx ks (coerce-fn coercion-schema (get-in ctx ks)))
          ctx))
      context coercions)
    context))

(defn input-coerce! [context schema matcher]
  (if-not (kc/any-map-schema? schema)
    (as-> context context
          (if matcher
            (coerce! schema matcher context nil ::request)
            context)
          (context-coerce! context schema))
    context))

(s/defn context-copy
  "Returns a function that assocs in a value from to-kws path into from-kws in a context"
  [from :- [s/Any], to :- [s/Any]]
  (s/fn [context :- Context]
    (assoc-in context to (get-in context from {}))))

(s/defn context-dissoc [from-kws :- [s/Any]]
  "Returns a function that dissocs in a value from from-kws in a context"
  (s/fn [context :- Context]
    (kc/dissoc-in context from-kws)))

;;
;; InMemoryDispatcher
;;

(s/defrecord InMemoryDispatcher
  [handlers :- {s/Keyword Handler}
   context :- KeywordMap
   coercion :- {:input s/Any
                :output s/Any}
   transformers :- [Function]
   user :- KeywordMap]

  IDispatcher
  (get-handlers [_]
    handlers)

  (dispatch [dispatcher mode action context]
    (if-let [{:keys [function all-user input output] :as handler} (some-handler dispatcher action)]
      (let [input-matcher (-> dispatcher :coercion :input)
            context (as-> context context

                          ;; TODO: in what order are these run? -> back to namespaces...

                          ;; base-context from Dispatcher
                          (kc/deep-merge (:context dispatcher) context)

                          ;; run all the transformers
                          ;; short-circuit execution if a transformer returns nil
                          (reduce (fn [ctx mapper] (or (mapper ctx) (reduced nil))) context (:transformers dispatcher))

                          ;; run all the user transformers per namespace/handler
                          ;; start from the root. a returned nil context short-circuits
                          ;; the run an causes ::dispatch error. Apply local coercion
                          ;; in the input is defined (using same definitions as with handlers)
                          (reduce
                            (fn [ctx [k v]]
                              (if-let [mapper-gen (get-in dispatcher [:user k])]
                                (let [mapper (mapper-gen v)
                                      input-schema (:input (extract-schema mapper))
                                      ;; TODO: automatic coercion = too much magic? just coerce :data?
                                      ctx (input-coerce! ctx input-schema input-matcher)]
                                  (or (mapper ctx) (reduced nil)))
                                ctx))
                            context
                            (apply concat all-user))

                          ;; run context coercion for :validate|:invoke and if context coercion is set
                          (cond-> context (and context (#{:validate :invoke} mode))
                                  ((fn [ctx] (input-coerce! ctx input input-matcher))))

                          ;; inject in stuff the context if not nil
                          (cond-> context context (merge context {::dispatcher dispatcher
                                                                  ::handler handler})))]

        (when-not context
          (throw (ex-info (str "Invalid action") {:type ::dispatch, :value action})))

        ;; all good, let's invoke?
        (if (#{:invoke} mode)
          (let [response (function context)]
            ;; response coercion
            (if (and output (-> dispatcher :coercion :output))
              (coerce! output (-> dispatcher :coercion :output) response nil ::response)
              response))))
      (throw (ex-info (str "Invalid action") {:type ::dispatch, :value action})))))

(s/defschema Options
  {:handlers KeywordMap
   (s/optional-key :context) KeywordMap
   (s/optional-key :type-resolver) Function
   (s/optional-key :transformers) [Function]
   (s/optional-key :coercion) {(s/optional-key :input) s/Any
                               (s/optional-key :output) s/Any}
   (s/optional-key :user) KeywordMap
   s/Keyword s/Any})

(s/def +default-options+ :- Options
  {:handlers {}
   :context {}
   :transformers []
   :coercion {:input (constantly nil)
              :output (constantly nil)}
   :type-resolver default-type-resolver
   :user {}})

(defn- collect-and-enrich
  [{:keys [handlers type-resolver user]} allow-empty-namespaces?]
  (let [handler-ns (fn [m] (if (seq m) (->> m (map :name) (map name) (str/join ".") keyword)))
        collect-ns-meta (fn [m] (if (seq m) (->> m (map :meta) (filterv (complement empty?)))))
        handler-action (fn [n ns] (keyword (str/join "/" (map name (filter identity [ns n])))))
        enrich (fn [h m]
                 (if (or (seq m) allow-empty-namespaces?)
                   (let [ns (handler-ns m)
                         ns-user (collect-ns-meta m)
                         user-meta (:user h)
                         all-user (if-not (empty? user-meta) (conj ns-user user-meta) ns-user)
                         user-input (reduce
                                      (fn [acc [k v]]
                                        (if-let [f (user k)]
                                          (let [schema (:input (extract-schema (f v)))]
                                            (kc/merge-map-schemas acc schema))
                                          acc)) {} (apply concat all-user))
                         action-input (kc/merge-map-schemas (:input h) user-input)]
                     (merge h {:ns ns
                               :ns-user ns-user
                               :all-user all-user
                               :action-input action-input
                               :action (handler-action (:name h) ns)}))
                   (throw (ex-info "can't define handlers into empty namespace" {:handler h}))))
        traverse (fn traverse [x m]
                   (flatten
                     (for [[k v] x]
                       (if (handler? v)
                         (enrich v m)
                         (traverse v (conj m k))))))]
    (-> handlers
        (collect type-resolver)
        (traverse [])
        (->> (group-by :action)
             (p/map-vals first)))))

(s/defn dispatcher :- Dispatcher
  "Creates a InMemoryDispatcher."
  [options :- Options]
  (let [options (kc/deep-merge +default-options+ options)
        handlers (->> (collect-and-enrich options false))]
    (map->InMemoryDispatcher
      (merge
        (select-keys options [:context :transformers :coercion :user])
        {:handlers handlers}))))

(s/defn transform-handlers
  "Applies f to all handlers. If the call returns nil,
  the handler is removed."
  [dispatcher :- Dispatcher, f :- Function]
  (update-in dispatcher [:handlers] (fn [handlers]
                                      (->> handlers
                                           (p/map-vals f)
                                           (filter (p/fn-> second))
                                           (into {})))))

;; TODO: works just with the InMemoryDispatcher -> publish to IDispatcher?
(s/defn inject
  "Injects handlers into an existing Dispatcher"
  [in-memory-dispatcher :- InMemoryDispatcher, handlers]
  (if handlers
    (let [handler (collect-and-enrich
                    (merge in-memory-dispatcher {:handlers handlers :type-resolver any-type-resolver}) true)]
      (update-in in-memory-dispatcher [:handlers] merge handler))))

;;
;; Calling handlers
;;

(s/defn check
  "Checks an action handler with the given context."
  ([dispatcher :- Dispatcher, action :- s/Keyword]
    (check dispatcher action {}))
  ([dispatcher :- Dispatcher, action :- s/Keyword, context :- Context]
    (dispatch dispatcher :check action context)))

(s/defn validate
  "Checks if context is valid for the handler (without calling the body).
  Returns nil or throws an exception."
  ([dispatcher :- Dispatcher, action :- s/Keyword]
    (validate dispatcher action {}))
  ([dispatcher :- Dispatcher, action :- s/Keyword, context :- Context]
    (dispatch dispatcher :validate action context)))

(s/defn invoke
  "Invokes an action handler with the given context."
  ([dispatcher :- Dispatcher, action :- s/Keyword]
    (invoke dispatcher action {}))
  ([dispatcher :- Dispatcher, action :- s/Keyword, context :- Context]
    (dispatch dispatcher :invoke action context)))

;;
;; Listing handlers
;;

(def DispatchHandlersMode (s/enum :available :check :validate))

(defn- filter-by-path [handlers path]
  (if-not path
    handlers
    (seq
      (filter
        (fn [{:keys [ns]}]
          (if ns
            (let [path-seq (str/split (subs (str path) 1) #"[\.]")
                  action-seq (str/split (subs (str ns) 1) #"[\.]")]
              (= path-seq (take (count path-seq) action-seq)))
            true))
        handlers))))

(defn- map-handlers [dispatcher mode prefix context success failure]
  (-> dispatcher
      get-handlers
      vals
      (filter-by-path prefix)
      (->>
        (map
          (fn [handler]
            (try
              (when-not (= mode :all)
                (dispatch dispatcher mode (:action handler) context))
              [handler (success handler)]
              (catch Exception e
                (if (-> e ex-data :type (= ::dispatch))
                  [nil nil]
                  [handler (failure e)])))))
        (filter first)
        (into {}))))

(s/defn all-handlers :- [Handler]
  "Returns all handlers filtered by namespace"
  [dispatcher :- Dispatcher
   prefix :- (s/maybe s/Keyword)]
  (keep second (map-handlers dispatcher :all prefix {} identity (constantly nil))))

(s/defn available-handlers :- [Handler]
  "Returns all available handlers based on namespace and context"
  [dispatcher :- Dispatcher
   prefix :- (s/maybe s/Keyword)
   context :- Context]
  (keep first (map-handlers dispatcher :check prefix context identity (constantly nil))))

; TODO: ring should be a dispatcher to get the coercions right, also :ring -filtering there
; TODO: test via ring
; TODO: update docs
(s/defn dispatch-handlers :- {Handler s/Any}
  "Returns a map of action -> errors based on mode, namespace and context."
  [dispatcher :- Dispatcher
   mode :- DispatchHandlersMode
   prefix :- (s/maybe s/Keyword)
   context :- Context]
  (let [[mode failure] (if (= mode :available) [:check (constantly nil)] [mode ex-data])]
    (map-handlers dispatcher mode prefix context (constantly nil) failure)))
