import Foundation

struct DemoFeedItem: Identifiable, Codable {
    let id: String
    let author: String
    let content: String
    let category: String // "neutral" | "rage-bait" | "toxic"
}

struct DemoFeedProvider {
    static let samplePosts: [DemoFeedItem] = [
        DemoFeedItem(
            id: "1",
            author: "@tech_insights",
            content: "Just released a new open-source library for state management in React Native!",
            category: "neutral"
        ),
        DemoFeedItem(
            id: "2",
            author: "@daily_news",
            content: "Morning weather report: Clear skies and mild temperatures expected throughout the weekend.",
            category: "neutral"
        ),
        DemoFeedItem(
            id: "3",
            author: "@outrage_loop",
            content: "THIS UNBELIEVABLE CHANGE WILL DESTROY EVERYTHING YOU CARE ABOUT! SHARE BEFORE IT IS DELETED!!!",
            category: "rage-bait"
        ),
        DemoFeedItem(
            id: "4",
            author: "@nature_shots",
            content: "Beautiful sunrise over the national park mountains today. Wishing everyone a great morning.",
            category: "neutral"
        ),
        DemoFeedItem(
            id: "5",
            author: "@toxic_commenter",
            content: "Anyone who disagrees with this obvious truth is completely ignorant and deserves total ruin.",
            category: "toxic"
        ),
        DemoFeedItem(
            id: "6",
            author: "@dev_community",
            content: "What is your favorite TypeScript feature introduced in version 5.0?",
            category: "neutral"
        ),
        DemoFeedItem(
            id: "7",
            author: "@rage_bait_daily",
            content: "EXPOSED: You won't believe what they secretly did behind closed doors to ruin the economy!",
            category: "rage-bait"
        ),
        DemoFeedItem(
            id: "8",
            author: "@mindful_living",
            content: "Taking a 10-minute break away from screens can help restore focus and calm.",
            category: "neutral"
        )
    ]
}
