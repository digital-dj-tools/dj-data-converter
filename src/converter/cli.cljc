(ns converter.cli
  (:require
   #?(:cljs [cljs.nodejs :as nodejs])
   #?(:clj [clojure.java.io :as io] :cljs [cljs-node-io.core :as io :refer [slurp spit]])
   #?(:cljs [converter.xmldom])
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.tools.cli :as cli]
   [converter.app :as app]
   [converter.error :as err]
   [converter.xml]
   [tick.alpha.api :as tick])
  #?(:clj (:gen-class)))

(def default-arguments
  {:output-file "rekordbox.xml"})

(def option-specs
  [["-h" "--help"]])

(defn usage-message
  [summary]
  (->> ["Usage: dj-data-converter [options] <input-file>"
        ""
        "Options:"
        summary]
       (string/join \newline)))

(defn error-message
  [errors]
  (as-> ["The following errors occurred while parsing the command:"
         ""] $
    (concat $ errors)
    (string/join \newline $)))

(defn parse-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args option-specs)]
    (cond
      (:help options) {:exit-message (usage-message summary) :help? true}
      errors {:exit-message (error-message errors)}
      (= 1 (count arguments)) {:arguments (merge default-arguments {:input-file (first arguments)})
                               :options options}
      :else {:exit-message (usage-message summary)})))

; TODO move to utils
(defn println-err
  [& objs]
  #?(:clj (binding [*out* *err*]
            (apply println objs))
     :cljs (binding [*print-fn* *print-err-fn*]
             (apply println objs))))

; TODO move to utils
(defn exit
  [status message]
  (if (= 0 status)
    (println message)
    (println-err message))
  #?(:clj (System/exit status)
     :cljs (.exit nodejs/process status)))

(defn output-dir
  [{:keys [output-file]}]
  (or (.getParent (io/file output-file))
      ""))

(defprotocol ArgumentsToConverter
  (converter [this arguments]))

; for basic edition
(def arguments-to-basic-converter
  (reify
    ArgumentsToConverter
    (converter [this {:keys [input-file] :as arguments}]
      (cond 
        (string/ends-with? input-file ".nml") app/traktor->rekordbox
        (string/ends-with? input-file ".xml") app/rekordbox->traktor
        :else (throw (ex-info "Could not determine converter for given arguments" {:arguments arguments}))))))

#?(:clj
   (defn process
     [arguments-to-converter config arguments options]
     (try
       (with-open [reader (io/reader (:input-file arguments))
                   writer (io/writer (:output-file arguments))]
         (as-> reader $
           (xml/parse $ :skip-whitespace true)
           (app/convert (converter arguments-to-converter arguments) config $)
           (xml/emit $ writer)))
       [0 "Conversion completed"]
       (catch Throwable t (do
                            (-> t
                                Throwable->map
                                (err/create-report arguments options)
                                (err/write-report (output-dir arguments)))
                            [2 "Problems converting, please provide error-report.edn file..."])))))

#?(:cljs
   (defn process
     [arguments-to-converter config arguments options]
     (try
       (as-> (:input-file arguments) $
         (io/slurp $)
         (xml/parse-str $)
         (converter.xml/strip-whitespace $)
         (app/convert (converter arguments-to-converter arguments) config $)
         (xml/emit-str $)
         (io/spit (:output-file arguments) $))
       [0 "Conversion completed"]
       (catch :default e (do
                           (-> e
                               err/Error->map
                               (err/create-report arguments options)
                               (err/write-report (output-dir arguments)))
                           [2 "Problems converting, please provide error-report.edn file..."])))))

(defn print-progress
  [f]
  (let [item-count (atom 1)]
    (fn [item]
      (when (= 0 (mod @item-count 1000))
        (println ".")
        #?(:clj (flush)))
      (swap! item-count inc)
      (f item))))

(def config
  {:progress print-progress
   :clock (tick/clock)})

(defn -main
  [& args]
  (let [{:keys [arguments options exit-message help?]} (parse-args args)]
    (if exit-message
      (exit (if help? 0 1) exit-message)
      (apply exit (process arguments-to-basic-converter config arguments options)))))

#?(:cljs (set! *main-cli-fn* -main))
