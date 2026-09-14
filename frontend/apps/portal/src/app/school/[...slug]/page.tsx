import { EmptyState } from "@tuition/ui";

export default async function SchoolComingSoonPage({ params }: { params: Promise<{ slug: string[] }> }) {
  const { slug } = await params;
  return (
    <EmptyState
      title="Not built yet"
      description={`/school/${slug.join("/")} is a separate ticket (SP-P2..SP-P11) - the sidebar link is real, the screen isn't.`}
    />
  );
}
