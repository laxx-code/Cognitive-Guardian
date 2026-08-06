import Foundation
import React

@objc(GuardianBridge)
class GuardianBridge: RCTEventEmitter {

    private var timer: Timer?
    private let modelBridge = FoundationModelsBridge()

    override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    override func supportedEvents() -> [String]! {
        return ["onTextBatch", "onOverlayNeeded"]
    }

    @objc func startDemoFeed() {
        print("[GuardianBridge] startDemoFeed invoked.")
        stopDemoFeed()

        // Periodically emit sample posts from DemoFeedProvider to JS buffer
        var index = 0
        let posts = DemoFeedProvider.samplePosts

        timer = Timer.scheduledTimer(withTimeInterval: 4.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            guard index < posts.count else {
                index = 0
                return
            }

            let item = posts[index]
            index += 1

            self.sendEvent(withName: "onTextBatch", body: [item.content])

            // Run the actual on-device classification pipeline rather than
            // trusting the mock feed's pre-labeled category. This is what
            // proves the model + overlay logic genuinely work end-to-end.
            let rawResponse = self.modelBridge.classifyBatch(snippets: [item.content])
            if let classification = GuardianBridge.parseClassification(rawResponse),
               classification.toxic {
                let flaggedText = classification.flaggedText ?? item.content
                self.sendEvent(withName: "onOverlayNeeded", body: ["flaggedText": flaggedText])
            }
        }
    }

    @objc func stopDemoFeed() {
        print("[GuardianBridge] stopDemoFeed invoked.")
        timer?.invalidate()
        timer = nil
    }

    /// Strict parse matching src/ai/responseParser.ts: fail safe to non-toxic
    /// on any malformed or unexpected shape rather than throwing.
    private static func parseClassification(_ raw: String) -> (toxic: Bool, flaggedText: String?)? {
        guard let data = raw.data(using: .utf8) else { return nil }
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        guard let toxic = obj["toxic"] as? Bool else { return nil }
        let flaggedText = obj["flaggedText"] as? String
        return (toxic: toxic, flaggedText: flaggedText)
    }
}
