(ns fscrawler-tika-convert.core
  (:require [langohr.basic :as lb])
  (:require [langohr.core :as rmq])
  (:require [langohr.queue :as lq])
  (:require [langohr.channel :as lch])
  (:require [langohr.consumers :as lcons])
  (:require [me.raynes.fs :as fs])
  (:require [clojure.tools.cli :as cli])
  (:require [clojure.data.json :as json])
  (:require [clojure.string :as string])
  (:require [tika])
  (:import java.io.File)
  (:gen-class))


(defn handle-message
  [ch metadata ^bytes payload]
  (let [body (json/read-json (String. payload "UTF-8"))
        ;; {:keys [directory relpath] body}
        directory (:directory body)
        relpath (:relpath (:entry body))
        fp (string/join File/separator [directory relpath])
        converted (tika/parse fp)
        ]
        ;;
    (println "dir" fp " ****" (:content-type converted))))

;; (println "got message" metadata (String. payload "UTF-8")))


(defn run-with-connection
  []
  (let [conn       (rmq/connect)
        ch         (lch/open conn)
        queue-name "nextbot.extract_content.fscrawler:test"
        handler    (fn [ch {:keys [headers delivery-tag redelivery?]} ^bytes payload]
                     (println "hello")
                     (println "headers" headers)
                     ;; (println (format "[consumer] Received %s" (String. payload "UTF-8")))
                     (lb/ack ch delivery-tag))]
    ;; (lq/declare ch queue-name :exclusive false :auto-delete true)
    ;; (lq/bind    ch queue-name "nextbot")
    (lcons/subscribe ch queue-name handle-message :auto-ack false)))


(defn -main [& args]
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))


  (let [[options args banner]
        (cli/cli args
                 ["--ampqp-url" "amqp url to connect to"]
                 ["--port" "Port to listen on" :default 5000]
                 ["--root" "Root directory of web server" :default "public"])]
    (println "port:" (:port options))
    (println "root:" (:root options)))
  (run-with-connection)
)