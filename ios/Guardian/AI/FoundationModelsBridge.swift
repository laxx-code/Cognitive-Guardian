import Foundation

/**
 * iOS On-Device Apple Foundation Models Bridge.
 * Interfaces with SystemLanguageModel on iOS 18.1+ / A17 Pro+ hardware.
 * Uses shared GUARDIAN_PROMPT contract.
 */
struct FoundationModelsBridge {
    // CONTRACT: keep in sync with src/ai/prompt.ts — do not change whitespace/wording
    static let sharedPrompt = "\nYou will receive an array of text snippets read from a screen over the last 30 seconds.\nClassify the OVERALL batch. Respond with ONLY valid JSON, no extra text:\n\n{\"toxic\": boolean, \"sentiment\": \"neutral\" | \"rage-bait\" | \"toxic\" | \"adult\", \"flaggedText\": string | null}\n\nSnippets:\n"

    func classifyBatch(snippets: [String]) -> String {
        let promptText = "\(FoundationModelsBridge.sharedPrompt)\n" + snippets.joined(separator: "\n- ")

        // TODO: On iOS 18.1+ / A17 Pro+ physical test device, call:
        // let session = try SystemLanguageModel.Session()
        // let response = try await session.respond(to: promptText)
        // return response.text

        // Mock fallback for simulator & build sanity checks:
        let isToxic = snippets.contains { item in
            let lower = item.lowercased()
            return lower.contains("destroy") || lower.contains("ruin") || lower.contains("ignorant") || lower.contains("exposed") || lower.contains("killer")
        }

        if isToxic {
            let firstToxicItem = snippets.first { item in
                let lower = item.lowercased()
                return lower.contains("destroy") || lower.contains("ruin") || lower.contains("ignorant") || lower.contains("exposed") || lower.contains("killer")
            } ?? snippets.first ?? "Toxic post"

            return "{\"toxic\": true, \"sentiment\": \"toxic\", \"flaggedText\": \"\(firstToxicItem)\"}"
        } else {
            return "{\"toxic\": false, \"sentiment\": \"neutral\", \"flaggedText\": null}"
        }
    }
}
