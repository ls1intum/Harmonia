import { BrowserRouter, Routes, Route } from "react-router-dom";
import Home from "@/pages/Home";
import Teams from "@/pages/Teams";
import TeamDetailPage from "@/pages/TeamDetailPage";
import NotFound from "@/pages/NotFound";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";

const App = () => (
    <TooltipProvider>
        <Toaster />
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Home />} />
                <Route path="/teams" element={<Teams />} />
                <Route path="/teams/:teamId" element={<TeamDetailPage />} />
                <Route path="*" element={<NotFound />} />
            </Routes>
        </BrowserRouter>
    </TooltipProvider>
);

export default App;