## 0.5.0 (2019-11-01)

* Make Kekkonen compatible with Cloverage.
* Update dependencies to support Java 13.
* Fix returning byte arrays with `kekkonen.upload/response`

## 0.4.0 (21.6.2017)

* **BREAKING**: Drops Java 1.6 Compatability (due to Muuntaja)
* **BREAKING**: use [Muuntaja](https://github.com/metosin/muuntaja) instead of [ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format), [#255](https://github.com/metosin/compojure-api/pull/255)
  for format negotiation, encoding and decoding.
  - ?x more throughput on 1k JSON request-response echo
  - api options `[:mw :format]` has been deprecated (fails at api creation time), use `:formats` instead. It consumes either a
    Muuntaja instance, Muuntaja options map or `::kekkonen.middleware/defaults` (for defaults). See [how to configure Muuntaja](https://github.com/metosin/muuntaja/wiki/Configuration) how to use.

* Updated deps:

```clj
[clj-http "2.3.0"] is available but we use "2.2.0"
[metosin/muuntaja "0.3.1"] is available but we use "0.3.0"
[metosin/ring-http-response "0.9.0"] is available but we use "0.8.1"
[metosin/ring-swagger "0.24.0"] is available but we use "0.23.0"
[metosin/ring-swagger-ui "2.2.10"] is available but we use "2.2.8"
[prismatic/plumbing "0.5.4"] is available but we use "0.5.3"
[prismatic/schema "1.1.6"] is available but we use "1.1.3"
[ring-middleware-format "0.7.2"] is available but we use "0.7.0"
[ring/ring-defaults "0.3.0"] is available but we use "0.2.1"
```

* Removed deps:

```clj
[ring-middleware-format "0.7.0"]
```

## 0.3.4 (11.1.2017)

* Updated dependencies to [avoid a path traversal vulnerability](https://groups.google.com/forum/#!topic/clojure/YDrKBV26rnA) in Ring.

```clj
[frankiesardo/linked "1.2.9"] is available but we use "1.2.8"
[metosin/ring-swagger "0.22.14"] is available but we use "0.22.10"
[metosin/ring-swagger-ui "2.2.8"] is available but we use "2.2.2-0"
[metosin/ring-http-response "0.8.1"] is available but we use "0.8.0"
```

## 0.3.3 (29.8.2016)

* Ring-coercion is applied also for `:default`, fixes [#45](https://github.com/metosin/kekkonen/issues/45)

* updated dependencies:

```clj
[prismatic/schema "1.1.3"] is available but we use "1.1.2"
[metosin/ring-swagger "0.22.10"] is available but we use "0.22.9"
[metosin/ring-swagger-ui "2.2.2-0"] is available but we use "2.1.4-0"
[frankiesardo/linked "1.2.8"] is available but we use "1.2.6"
```

## 0.3.2 (1.7.2016)

* `kekkonen.upload/response` for easy returning of file-respones (uploads still Alpha)

* updated dependencies:

```clj
[metosin/ring-http-response "0.8.0"] is available but we use "0.7.0"
[ring/ring-defaults "0.2.1"]
```

## 0.3.1 (28.6.2016)

* Alpha support for (ring-based) file uploads
  * `kekkonen.upload/multipart-params` interceptor, uses `ring.middleware.multipart-params/multipart-params-request` (same options)
  * `kekkonen.upload/TempFileUpload` & `kekkonen.upload/ByteArrayUpload` as swagger-aware types
  * `:kekkonen.ring/consumes` & `:kekkonen.ring/produces` - meta-data, just for docs now
  
```clj
(defnk upload
  "upload a file to the server"
  {:interceptors [[upload/multipart-params]]
   :type ::ring/handler
   ::ring/method :put
   ::ring/consumes ["multipart/form-data"]}
  [[:request [:multipart-params file :- upload/TempFileUpload]]]
  (ok (dissoc file :tempfile)))

(def app
  (api
    {:swagger {:ui "/api-docs"
               :spec "/swagger.json"}
     :api {:handlers {:http #'upload}}}))
```

## 0.3.0 (27.6.2016)

* **BREAKING**: Removed type-level interceptors from ring-adapter, use normal interceptors instead.
* **BREAKING**: Ring request-parameters are now assoc-in'd (into `:data`) instead of deep-merging. For speed.
* Handlers can be now be mounted to dispatcher root.
* Removed `kekkonen.core/simple-coercion`, renamed `multi-coercion` to `coercion`.
* Support for Context-based urls, thanks to [Wout Neirynck](https://github.com/wneirynck).
* Data input schemas for apis can be vectors, fixes [#27](https://github.com/metosin/kekkonen/issues/27).
* Use Pedestal-style interceptors, with `:name`, `:enter`, `:leave` and `:error`
  * Extended to contain `:input` and `:output` schemas.
* Exceptions raised in the interceptor chain are rethrown as wrapped (Pedestal) exceptions,
containing extra meta-data of the failed step: `:execution-id`, `:stage`, `:interceptor`, `:exception-type` and `:exception`.
  * extra fields are removed in the api exception handling
* `kekkonen.core/request` & `kekkonen.core/response` exception handeled gracefully with the `api`s
* Createing an `api` doesn't force schema validation by default
* Interceptors are pre-compiled into Records in all layers for simplicity and better perf.
* Remove the following excess meta-data from handlers: 
  * `:ns-meta`, `:all-meta`, `:handler-input` & `:user-input`
* Remove `:interceptors` from the `Dispatcher`, as they are now precompiled into handlers
* Interceptors can be `nil`, allowing conditional interceptors

```clj
(k/handler
  {:name "fixture!"
   :interceptors [[require-role :admin] (if-not env/dev-mode? log-it)]
   :handle (fn [ctx] ...)})
```

* **BREAKING**: top-level swagger options are now in align to the compojure-api:
  * Fixes [#22](https://github.com/metosin/kekkonen/issues/22)
  * By default, `api`s don't bind swagger-spec & swagger-ui, use `:spec` & `:ui` options

### Old

```clj
{:swagger {:info {:title "Kekkonen"}}
 :swagger-ui {:jsonEdit true}})
```

### New

```clj
{:swagger
 {:spec "/swagger.json"
  :ui "/api-docs"
  :options {:ui {:jsonEdit true}
            :spec {:ignore-missing-mappings? false}}
  :data {:info {:title "Kekkonen"}}}}
```

* **BREAKING**: Handler dispatch function is now `:handle` instead of `:function`
* Handlers can be defined via a single map with `:handle` key for the dispatch

```clj
(k/handler
  {:name "hello"
   :handle (constantly "hello")})
```

* updated dependencies:

```clj
[prismatic/schema "1.1.2"] is available but we use "1.1.0"
[prismatic/plumbing "0.5.3"] is available but we use "0.5.2"
[metosin/ring-http-response "0.7.0"] is available but we use "0.6.5"
[metosin/ring-swagger "0.22.9"] is available but we use "0.22.6"
[clj-http "2.2.0"] available but we use "2.1.0"
```

## 0.2.0 (29.3.2016)

**[compare](https://github.com/metosin/kekkonen/compare/0.1.2...0.2.0)**

* Change Transformers to (initial version of) Interceptors in both the Dispatcher & Ring.
  * `:transformers`-key is replaced with `:interceptors`
  * Interceptors are either functions `context => context` (just like the old transformers) or maps
  with keys `:enter` and `:leave`. Will be later merged to use the [Pedestal](http://pedestal.io/) defined
  interceptors.
* User defined context-handers are now under `:meta` instead of `:user`.
* Defined `:meta` keys are checked at dispatcher creation time.
* By default, dispatcher will have a  `:interceptors` meta-key registered.
  * It takes an vector of interceptors as value, applied first to the namespace/handler
* Ring-adapter interceptors can use the dispatcher context, fixes [#26](https://github.com/metosin/kekkonen/issues/26)

#### Old syntax

```clj
(cqrs-api
  {:swagger {:info {:title "Kekkonen"}}
   :core {:handlers {:api {:math 'math
                           :system [#'ping #'pong]}}
          :transformers [log-commands]
          :user {::roles require-roles}}
   :ring {:transformers [api-key-authenticator]}})
```

#### New syntax

```clj
(cqrs-api
  {:swagger {:info {:title "Kekkonen"}}
   :core {:handlers {:api {:math 'math
                           :system [#'ping #'pong]}}
          :interceptors [log-commands]
          :meta {::roles require-roles}}
   :ring {:interceptors [api-key-authenticator]}})
```

* Updated dependencies

```clj
[prismatic/schema "1.1.0"] is available but we use "1.0.4"
[metosin/ring-swagger "0.22.6"] is available but we use "0.22.1"
[metosin/ring-swagger-ui "2.1.4-0"] is available but we use "2.1.3-4"
[clj-http "2.1.0"] is available but we use "2.0.0"
```

## 0.1.2 (30.12.2015)

* Fix client using wrong keywords [#16](https://github.com/metosin/kekkonen/pull/16)
* Deterministic order for user annotations [#15](https://github.com/metosin/kekkonen/pull/15)

```clj
[prismatic/schema "1.0.4"] is available but we use "1.0.3"
[metosin/ring-swagger "0.22.1"] is available but we use "0.22.0"
[metosin/ring-swagger-ui "2.1.3-4"] is available but we use "2.1.3-2"
```

## 0.1.1 (25.11.2015)

- Fix Transit format options

## 0.1.0 (10.11.2015)

- Initial public version
