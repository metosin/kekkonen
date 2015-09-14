# Kekkonen

A library for creating and consuming remote APIs for Clojure(Script). http://kekkonen.io/

Status: Alpha. `0.1.0` will be released soon.

# Mission statement

We are building complex UIs and need better tools to support that. Focus should on consuming
the apis, not just producing those (like REST does). One should be able to enforce business
rules both on the client-side & the server-side. State and dependencies should be managed too.

# Idea

- Simple **library** to create and consume apis
- Expose simple Clojure **functions** as message handlers
- Manage handlers in virtual **namespaces** to enable refactorings
- **Schema** for describing all the things
- Data-driven, no macros, **no magic**
- Declarative dependencies and state management
- Explicit **extensions** via protocols, options and **meta-data**
- **Transports** abstracted away
  - http via ring, websockets coming up
- **Clients** as first-class citizens
  - Remote **api documentation** as clojure/json data
    - client helpers for both ClojureScript & Javascript
  - Public http api documentation via **Swagger**
  
# A Simple example

```clojure
(ns example.api
  (:require [org.httpkit.server :as server]
            [kekkonen.cqrs :refer :all]
            [plumbing.core :as p]
            [schema.core :as s]))

;;
;; Schemas
;;

(s/defschema Pizza
  {:name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :S :M :L)
   :origin {:country (s/enum :FI :PO)}})

;;
;; Handlers
;;

(p/defnk ^:query ping []
  (success {:ping "pong"}))

(p/defnk ^:command echo-pizza
  "Echoes a pizza"
  {:responses {:default {:schema Pizza}}}
  [data :- Pizza]
  (success data))

(p/defnk ^:query plus
  [[:data x :- s/Int, y :- s/Int]]
  (success {:result (+ x y)}))

(p/defnk ^:command inc!
  [[:components counter]]
  (success {:result (swap! counter inc)}))

;;
;; Application
;;

(def app
  (cqrs-api
    {:info {:info {:title "Kekkonen example"}}
     :core {:handlers {:api {:example [#'echo-pizza #'plus #'ping]
                             :state #'inc!}}
            :context {:components {:counter (atom 0)}}}}))

;;
;; Start it
;;

(defn start []
  (server/run-server #'app {:port 3000}))

(comment
  (start))
```

Start the server and browse to http://localhost:3000 and you should see the following:

![swagger-example](https://raw.githubusercontent.com/wiki/metosin/kekkonen/swagger-example.png)

# Special thanks

- [Schema](https://github.com/Prismatic/schema) for everything
- [Plumbing](https://github.com/Prismatic/plumbing) for the `fnk`y syntax
- [Fnhouse](https://github.com/Prismatic/fnhouse) for inspiration
- [Ring-swagger](https://github.com/metosin/ring-swagger) for the Schema2Swagger -bindings
- [Ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format) for all the http-transports
- [Compojure-api](https://github.com/metosin/compojure-api) for some middleware goodies

## License

Copyright © 2015 Metosin Oy

Distributed under the Eclipse Public License, the same as Clojure.
