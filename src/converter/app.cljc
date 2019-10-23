(ns converter.app
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.config :as config]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.xml :as xml]
   [spec-tools.core :as st]
   #?(:clj [taoensso.tufte :as tufte :refer (defnp p profile)]
      :cljs [taoensso.tufte :as tufte :refer-macros (defnp p profile)])))

(defprotocol Converter
  (input-spec [this config])
  (input-string-transformer [this])
  (input-xml-transformer [this])
  (library-spec [this])
  (output-spec [this config])
  (output-xml-transformer [this]))

(def traktor->rekordbox
  (reify
    Converter
    (input-spec
      [this config]
      (t/nml-spec config))
    (input-string-transformer
      [this]
      t/string-transformer)
    (input-xml-transformer
      [this]
      t/xml-transformer)
    (library-spec
      [this]
      t/library-spec)
    (output-spec
      [this config]
      (r/dj-playlists-spec config))
    (output-xml-transformer
      [this]
      r/xml-transformer)))

(def rekordbox->traktor
  (reify
    Converter
    (input-spec
      [this config]
      (r/dj-playlists-spec config))
    (input-string-transformer
      [this]
      r/string-transformer)
    (input-xml-transformer
      [this]
      r/xml-transformer)
    (library-spec
      [this]
      r/library-spec)
    (output-spec
      [this config]
      (t/nml-spec config))
    (output-xml-transformer
      [this]
      t/xml-transformer)))

(defprotocol Edition
  (converter [this config]))

(def basic-edition
  (reify
    Edition
    (converter [this {:keys [input]}]
      (cond
        ; TODO either throw exception if input is anything else
        ; or guarantee it isn't by spec conform etc
        (= input :traktor) traktor->rekordbox
        (= input :rekordbox) rekordbox->traktor))))

(s/fdef convert
  :args (s/cat
         :converter #{traktor->rekordbox}
         :config (spec/such-that-spec config/config-spec #(= :traktor (:input %)))
         :xml (spec/value-encoded-spec (t/nml-spec {}) t/string-transformer))
  :ret (spec/value-encoded-spec (r/dj-playlists-spec {}) r/string-transformer))
; TODO :ret spec should OR with some spec that checks all leafs are strings

(defn convert
  [converter config xml]
  (let [input-spec (input-spec converter config)
        library-spec (library-spec converter)
        output-spec (output-spec converter config)]
    (as-> xml $
      (p ::decode-1 (spec/decode! input-spec $ (input-string-transformer converter)))
      (p ::decode-2 (spec/decode! library-spec $ (input-xml-transformer converter)))
      ; FIXME skip spec tools encode for traktor output (performance issue)
      (if (= (:output config) :traktor)
        (p ::encode-nml (t/library->nml config nil $))
        (p ::encode (st/encode output-spec $ (output-xml-transformer converter)))))))
