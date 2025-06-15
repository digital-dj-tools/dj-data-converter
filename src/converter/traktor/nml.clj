(set! *warn-on-reflection* true)
(ns converter.traktor.nml
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [converter.time :as time]
   [converter.str :as str]))

(def nml-path-sep
  "/:")

(def nml-path-sep-regex
  #"/:")

(defn nml-dir
  [dirs]
  (as-> dirs $
    (interleave (repeat nml-path-sep) $)
    (vec $)
    (conj $ nml-path-sep)
    (apply str $)))

(defn nml-dir-gen
  []
  (gen/fmap nml-dir (gen/vector (str/not-blank-string-with-whitespace-gen))))

(def nml-dir-regex
  #"(?:/:.+)*/:")

(s/def ::nml-dir
  (s/with-gen
    (s/and string? #(re-matches nml-dir-regex %))
    (fn [] (nml-dir-gen))))

(s/def ::nml-path
  (s/with-gen
    string? ; TODO and with cat+regex specs
    (fn [] (gen/fmap (partial apply str)
                     (gen/tuple
                      ; drive letter (optional)
                      (gen/one-of [(str/drive-letter-gen) (gen/elements #{""})])
                      ; dir
                      (nml-dir-gen)
                      ; filename
                      (str/not-blank-string-with-whitespace-gen))))))

(def nml-date-format "yyyy/M/d")

; FIXME conversion functions to be used where ::time/date-str is being used instead of ::time/date
; either due to https://github.com/metosin/spec-tools/issues/183, or due to spec-tools encode being
; skipped for performance reasons

(defn string->date
  [str]
  (time/string->date nml-date-format nil str))

(defn date->string
  [date]
  (time/date->string nml-date-format nil date))
