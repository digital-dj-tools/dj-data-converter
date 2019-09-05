(ns converter.app
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.xml :as xml]
   [spec-tools.core :as st]))

(defprotocol TraktorRekordboxConverter
  (input-spec [this])
  (library-spec [this])
  (output-spec [this config]))

(def traktor->rekordbox
  (reify
    TraktorRekordboxConverter
    (input-spec
      [this]
      (t/nml-spec))
    (library-spec
      [this]
      t/library-spec)
    (output-spec
      [this progress]
      (r/dj-playlists-spec progress))))

(defn doto-prn
  [obj f]
  (prn (f obj)))

(s/fdef convert
  :args (s/cat :config #{{:converter traktor->rekordbox}}
               :xml (spec/value-encoded-spec (t/nml-spec) spec/string-transformer))
  :ret (spec/value-encoded-spec r/dj-playlists-spec spec/string-transformer))
; TODO :ret spec should OR with some spec that checks all leafs are strings

(defn convert
  [config xml]
  (let [input-spec (input-spec (:converter config))
        library-spec (library-spec (:converter config))
        output-spec (output-spec (:converter config) (:progress config))]
    (as-> xml $
      (spec/decode! input-spec $ spec/string-transformer)
      (spec/decode! library-spec $ spec/xml-transformer)
      (st/encode output-spec $ spec/xml-transformer))))