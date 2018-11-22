(ns converter.cli
  (:require
   #?(:clj [clojure.java.io :as io] :cljs [cljs-node-io.core :as io :refer [slurp spit]])
   #?(:cljs [converter.xmldom])
   [clojure.data.xml :as xml]
   [converter.app :as app]
   [converter.error :as err]
   [converter.xml])
  #?(:clj (:gen-class)))

(defn -main [& args]
  #?(:clj
     (try
       (with-open [reader (io/reader (first args))
                   writer (io/writer "rekordbox.xml")]
         (as-> reader $
           (xml/parse $ :skip-whitespace true)
           (app/convert $)
           (xml/emit $ writer)))
       (catch Throwable t (err/write-report! (err/create-report args (Throwable->map t)))))
     :cljs
     (try
       (as-> (first args) $
         (io/slurp $)
         (xml/parse-str $)
        ;  (converter.xml/strip-whitespace $)
         (app/convert $)
         (xml/emit-str $)
         (io/spit "rekordbox.xml" $))
       (catch :default e (err/write-report! (err/create-report args (err/Error->map e)))))))

#?(:cljs (set! *main-cli-fn* -main))
